package parser.zenodo;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.logging.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eosc.eu.ParsersConfig.ParserConfig;
import eosc.eu.ParserService;
import eosc.eu.TransferServiceException;
import eosc.eu.model.*;
import parser.ParserHelper;


/***
 * Class for parsing Zenodo DOIs
 */
public class ZenodoParser implements ParserService {

    private static final Logger LOG = Logger.getLogger(ZenodoParser.class);

    private String id;
    private String name;
    private int timeout;
    private String recordId;
    private static Zenodo parser;


    /***
     * Constructor
     * @param id The key of the parser in the config file
     */
    public ZenodoParser(String id) { this.id = id; }

    /***
     * Initialize the REST client for B2Share.
     * The hostname of the B2Share server should be already determined by a previous call to canParseDOI().
     * @param config Configuration loaded from the config file.
     * @return true on success
     */
    public boolean init(ParserConfig config) {

        this.name = config.name();
        this.timeout = config.timeout();

        if (null != this.parser)
            return true;

        LOG.debug("Obtaining REST client for Zenodo");

        // Check if base URL is valid
        URL urlParserService;
        try {
            var url = config.url().isPresent() ? config.url().get() : "";
            urlParserService = new URL(url);
        } catch (MalformedURLException e) {
            LOG.error(e.getMessage());
            return false;
        }

        try {
            // Create the REST client for the parser service
            this.parser = RestClientBuilder.newBuilder()
                            .baseUrl(urlParserService)
                            .build(Zenodo.class);

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
    public String sourceId() { return this.recordId; }

    /***
     * Checks if the parser service understands this DOI.
     * @param auth The access token needed to call the service.
     * @param doi The DOI for a data set.
     * @return Return true if the parser service can parse this DOI.
     */
    public Uni<Tuple2<Boolean, ParserService>> canParseDOI(String auth, String doi, ParserHelper helper) {
        // Validate DOI without actually fetching the URL
        boolean isValid = null != doi && !doi.isBlank();
        if(!isValid)
            return Uni.createFrom().failure(new TransferServiceException("doiInvalid"));

        // Check if DOI points/redirects to a Zenodo record
        var result = Uni.createFrom().item(helper.redirectedToUrl())

            .chain(redirectedToUrl -> {
                if(null != redirectedToUrl)
                    return Uni.createFrom().item(redirectedToUrl);

                return helper.checkRedirect(doi);
            })
            .chain(redirectedToUrl -> {
                if(!doi.equals(redirectedToUrl))
                    LOG.debugf("Redirected DOI %s", redirectedToUrl);

                // Validate URL
                Pattern p = Pattern.compile("^https?://([\\w\\.]*zenodo.org)/record/(\\d+)", Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(redirectedToUrl);
                boolean isSupported = m.matches();

                if(isSupported)
                    this.recordId = m.group(2);

                return Uni.createFrom().item(Tuple2.of(isSupported, (ParserService)this));
            })
            .onFailure().invoke(e -> {
                LOG.errorf("Failed to check if DOI %s points to Zenodo record", doi);
            });

        return result;
    }

    /**
     * Parse the DOI and return a set of files in the data set.
     * @param auth The access token needed to call the service.
     * @param doi The DOI for a data set.
     * @return List of files in the data set.
     */
    public Uni<StorageContent> parseDOI(String auth, String doi) {
        if(null == doi || doi.isBlank())
            return Uni.createFrom().failure(new TransferServiceException("doiInvalid"));

        if(null == this.parser)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        if(null == this.recordId || this.recordId.isEmpty())
            return Uni.createFrom().failure(new TransferServiceException("noRecordId"));

        Uni<StorageContent> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("doiParseTimeout"))
            .chain(unused -> {
                // Get Zenodo record details
                return this.parser.getRecordAsync(this.recordId);
            })
            .chain(record -> {
                // Got Zenodo record
                LOG.infof("Got Zenodo record %s", record.id);

                // Build list of source files
                StorageContent srcFiles = new StorageContent(record.files.size());
                for(var file : record.files) {
                    srcFiles.elements.add(new StorageElement(file));
                }

                srcFiles.count = srcFiles.elements.size();

                // Success
                return Uni.createFrom().item(srcFiles);
            })
            .onFailure().invoke(e -> {
                LOG.errorf("Failed to parse Zenodo DOI %s", doi);
            });

        return result;
    }

}
