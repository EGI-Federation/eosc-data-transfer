package parser.generic;

import eosc.eu.DataTransferSelf;
import eosc.eu.PortConfig;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.logging.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eosc.eu.ParsersConfig.ParserConfig;
import eosc.eu.ParserService;
import eosc.eu.TransferServiceException;
import eosc.eu.model.*;
import parser.generic.model.*;
import parser.ParserHelper;


/***
 * Class for parsing DOIs to pages that support Signposting
 */
public class SignpostParser implements ParserService {

    private static final int MAX_RECURSION = 3;
    private static final Logger LOG = Logger.getLogger(SignpostParser.class);

    private String id;
    private String name;
    private int timeout;
    private ParserHelper helper;
    private static DataTransferSelf parser;


    /***
     * Constructor
     * @param id The key of the parser in the config file
     */
    public SignpostParser(String id) { this.id = id; }

    /***
     * Initialize parser.
     * The helper should be already set by a previous call to canParseDOI().
     * @param config Configuration of the parser, from the config file.
     * @param port The port on which the application runs, from the config file.
     * @return true on success
     */
    public boolean init(ParserConfig config, PortConfig port) {
        this.name = config.name();
        this.timeout = config.timeout();

        if (null != parser)
            return true;

        LOG.debug("Obtaining REST client for ourselves");

        // Check if base URL is valid
        URL urlParserService;
        try {
            var urlSelf = String.format("http://localhost:%d", port.port());
            urlParserService = new URL(urlSelf);
        } catch (MalformedURLException e) {
            LOG.error(e.getMessage());
            return false;
        }

        try {
            // Create the REST client for ourselves
            parser = RestClientBuilder.newBuilder()
                    .baseUrl(urlParserService)
                    .build(DataTransferSelf.class);

            return true;
        }
        catch (RestClientDefinitionException e) {
            LOG.error(e.getMessage());
        }

        return false;
    }

    /***
     * Get the Id of the parser.
     * @return Id of the parser service.
     */
    public String getId() { return this.id; }

    /***
     * Get the human-readable name of the parser.
     * @return Name of the parser service.
     */
    public String getName() { return this.name; }

    /***
     * Get the Id of the source record.
     * @return Source record Id.
     */
    public String sourceId() { return ""; }

    /***
     * Checks if the parser service understands this DOI.
     * @param auth   The access token needed to call the service.
     * @param doi    The DOI for a data set.
     * @param helper Helper class that can follow (and cache) redirects.
     * @return Return true if the parser service can parse this DOI.
     */
    public Uni<Tuple2<Boolean, ParserService>> canParseDOI(String auth, String doi, ParserHelper helper) {
        boolean isValid = null != doi && !doi.isBlank();
        if(!isValid)
            return Uni.createFrom().failure(new TransferServiceException("doiInvalid"));

        this.helper = helper;

        // Follow the URL and inspect headers
        var result = Uni.createFrom().item(helper.redirectedToUrl())

            .chain(redirectedToUrl -> {
                if(null != redirectedToUrl)
                    return Uni.createFrom().item(Tuple2.of(redirectedToUrl, helper.headers()));

                return helper.fetchHeaders(doi);
            })
            .chain(target -> {
                // Get values in "Link" header
                var headers = target.getItem2();
                var links = headers.getAll("Link");
                boolean hasLinks = (null != links) && !links.isEmpty();

                return Uni.createFrom().item(Tuple2.of(hasLinks, (ParserService)this));
            })
            .onFailure().invoke(e -> {
                LOG.errorf("Failed to check if DOI %s supports Signposting", doi);
            });

        return result;
    }

    /**
     * Parse the DOI and return a set of files in the data set.
     * @param auth  The access token needed to call the service.
     * @param doi   The DOI for a data set.
     * @param level The level of recursion. If we have to call ourselves, this gets increased
     *              each time, providing for a mechanism to avoid infinite recursion.
     * @return List of files in the data set.
     */
    public Uni<StorageContent> parseDOI(String auth, String doi, int level) {
        if(null == doi || doi.isBlank())
            return Uni.createFrom().failure(new TransferServiceException("doiInvalid"));

        if(null == this.helper)
            return Uni.createFrom().failure(new TransferServiceException("noParseHelper"));

        // Follow the URL and inspect headers
        var result = Uni.createFrom().item(helper.redirectedToUrl())

                .chain(redirectedToUrl -> {
                    if(null != redirectedToUrl)
                        return Uni.createFrom().item(redirectedToUrl);

                    return Uni.createFrom().item(doi);
                })
                .onItem().transformToMulti(targetUrl -> {
                    // Get lines in "Link" header
                    var headers = this.helper.headers();
                    var rawLinks = headers.getAll("Link");
                    var links = new ArrayList<Link>();

                    for(var rawLink : rawLinks) {
                        String pattern = "\\s*<?(https?://[^>;]+)>?\\s*;\\s*rel\\s*=\\s*[\"\']([^\"\']+)[\"\']\\s*" +
                                         "(;\\s*type\\s*=\\s*[\"\'](?<type>[^\"\']+)[\"\'])?\\s*,?";
                        Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                        Matcher m = p.matcher(rawLink);
                        while(m.find()) {
                            var link = new Link(m.group(1), m.group(2), m.group("type"));
                            if(link.relation.equalsIgnoreCase("item") ||
                               link.relation.equalsIgnoreCase("linkset") ||
                               link.relation.equalsIgnoreCase("identifier")) {
                                // This link we can try to handle
                                links.add(link);
                            }
                        }
                    }

                    return Multi.createFrom().iterable(links);
                })
                .onItem().transformToUniAndConcatenate(link -> {
                    // Got some links
                    if(link.relation.equalsIgnoreCase("item")) {
                        // Content with one element
                        LOG.debugf("Signposting relation (%s) is %s", link.relation, link.url);
                        var content = new StorageContent();
                        var element = new StorageElement(link.url, link.type);
                        content.add(element);
                        return Uni.createFrom().item(content);
                    }
                    else if(link.relation.equalsIgnoreCase("linkset")){
                        // Content with multiple elements
                        LOG.debugf("Signposting relation (%s) is %s", link.relation, link.url);
                        return this.helper.fetchLinkset(link.url);
                    }
                    else if(link.relation.equalsIgnoreCase("identifier")){
                        // Content with a DOI
                        LOG.debugf("Signposting relation (%s) is %s", link.relation, link.url);
                        if(!doi.equalsIgnoreCase(link.url) && level <= MAX_RECURSION)
                            return parser.parseDOIAsync(auth, link.url, level + 1);
                    }

                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.errorf("Failed to parse Signposting DOI %s", doi);
                })
                .collect()
                .in(StorageContent::new, (acc, storage) -> {
                    acc.merge(storage);
                });

        return result;
    }

}
