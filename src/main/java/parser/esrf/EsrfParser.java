package parser.esrf;

import eosc.eu.PortConfig;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.logging.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eosc.eu.ParsersConfig.ParserConfig;
import eosc.eu.ParserService;
import eosc.eu.TransferServiceException;
import eosc.eu.model.*;
import parser.ParserHelper;
import parser.esrf.model.EsrfCredentials;


/***
 * Class for parsing ESRF DOIs
 */
public class EsrfParser implements ParserService {

    private static final Logger LOG = Logger.getLogger(EsrfParser.class);

    private String id;
    private String name;
    private int timeout;
    private String baseUrl;
    private String authority;
    private String recordId;
    private static Esrf parser;


    /***
     * Constructor
     * @param id The key of the parser in the config file
     */
    public EsrfParser(String id) { this.id = id; }

    /***
     * Initialize the REST client for ESRF.
     * @param config Configuration of the parser, from the config file.
     * @param port The port on which the application runs, from the config file.
     * @return true on success
     */
    public boolean init(ParserConfig config, PortConfig port) {
        this.name = config.name();
        this.timeout = config.timeout();

        if (null != parser)
            return true;

        LOG.debug("Obtaining REST client for ESRF");

        // Check if base URL is valid
        URL urlParserService;
        try {
            this.baseUrl = config.url().isPresent() ? config.url().get() : "";
            urlParserService = new URL(this.baseUrl);

            if(!this.baseUrl.isEmpty() && '/' == this.baseUrl.charAt(this.baseUrl.length() - 1))
                this.baseUrl = this.baseUrl.replaceAll("[/]+$", "");

        } catch (MalformedURLException e) {
            LOG.error(e.getMessage());
            return false;
        }

        try {
            // Create the REST client for the parser service
            parser = RestClientBuilder.newBuilder()
                            .baseUrl(urlParserService)
                            .build(Esrf.class);

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
     * @param auth   The access token needed to call the service.
     * @param doi    The DOI for a data set.
     * @param helper Helper class that can follow (and cache) redirects.
     * @return Return true if the parser service can parse this DOI.
     */
    public Uni<Tuple2<Boolean, ParserService>> canParseDOI(String auth, String doi, ParserHelper helper) {
        boolean isValid = null != doi && !doi.isBlank();
        if(!isValid)
            return Uni.createFrom().failure(new TransferServiceException("doiInvalid"));

        // Check if DOI points/redirects to an ESRF record
        var result = Uni.createFrom().item(helper.redirectedToUrl())

            .chain(redirectedToUrl -> {
                if(null != redirectedToUrl)
                    return Uni.createFrom().item(redirectedToUrl);

                return helper.checkRedirect(doi);
            })
            .chain(redirectedToUrl -> {
                if(null == redirectedToUrl)
                    redirectedToUrl = doi;
                else if(!doi.equals(redirectedToUrl))
                    LOG.debugf("Redirected DOI %s", redirectedToUrl);

                // Validate URL
                Pattern p = Pattern.compile("^https?://([\\w\\.]*esrf.fr)/doi/([^/]+)/([^/#\\?]+)",
                                            Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(redirectedToUrl);
                boolean isSupported = m.matches();

                if(isSupported) {
                    this.authority = m.group(2);
                    this.recordId = m.group(3);
                }

                return Uni.createFrom().item(Tuple2.of(isSupported, (ParserService)this));
            })
            .onFailure().invoke(e -> {
                LOG.errorf("Failed to check if DOI %s points to ESRF record", doi);
            });

        return result;
    }

    /**
     * Parse the DOI and return a set of files in the data set.
     * @param auth  The access token needed to call the service.
     * @param doi   The DOI for a data set.
     * @param level Unused.
     * @return List of files in the data set.
     */
    public Uni<StorageContent> parseDOI(String auth, String doi, int level) {
        if(null == doi || doi.isBlank())
            return Uni.createFrom().failure(new TransferServiceException("doiInvalid"));

        if(null == parser)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        if(null == this.authority || this.authority.isEmpty() ||
           null == this.recordId || this.recordId.isEmpty())
            return Uni.createFrom().failure(new TransferServiceException("noRecordId"));

        AtomicReference<String> sessionId = new AtomicReference<>(null);
        Uni<StorageContent> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("doiParseTimeout"))
            .chain(unused -> {
                // Get an ESRF session
                return parser.getSessionAsync(new EsrfCredentials("reader", "reader"));
            })
            .chain(session -> {
                // Got a session
                if(null == session || null == session.sessionId)
                    return Uni.createFrom().failure(new TransferServiceException("noSessionId"));

                sessionId.set(session.sessionId);

                // Get the dataset
                return parser.getDataSetsAsync(sessionId.get(), this.authority, this.recordId);
            })
            .chain(datasets -> {
                // Got dataset(s)
                LOG.infof("Found %d datasets at DOI %s", datasets.size(), doi);

                // Handle the first dataset, ignore the rest
                var dataset = datasets.get(0);
                LOG.infof("First dataset has ID %s", dataset.id);

                return parser.getDataFilesAsync(sessionId.get(), dataset.id);
            })
            .chain(files -> {
                // Got the files in the dataset
                var session = sessionId.get();
                StorageContent srcFiles = new StorageContent(files.size());
                for(var file : files) {
                    srcFiles.elements.add(new StorageElement(file, this.baseUrl, session));
                }

                srcFiles.count = srcFiles.elements.size();

                // Success
                return Uni.createFrom().item(srcFiles);
            })
            .onFailure().invoke(e -> {
                LOG.errorf("Failed to parse ESRF DOI %s", doi);
            });

        return result;
    }

}
