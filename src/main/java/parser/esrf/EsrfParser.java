package parser.esrf;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eosc.eu.PortConfig;
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

    private static final Logger log = Logger.getLogger(EsrfParser.class);

    private String id;
    private String name;
    private int timeout;
    private String authority;
    private String recordId;
    private static String baseUrl;
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

        log.debug("Obtaining REST client for ESRF");

        // Check if base URL is valid
        URL urlParserService;
        try {
            baseUrl = config.url().isPresent() ? config.url().get() : "";
            urlParserService = new URL(baseUrl);

            if(!baseUrl.isEmpty() && '/' == baseUrl.charAt(baseUrl.length() - 1))
                baseUrl = baseUrl.replaceAll("[/]+$", "");

        } catch (MalformedURLException e) {
            log.error(e.getMessage());
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
            log.error(e.getMessage());
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

        log.debug("Check if DOI points to ESRF record");

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
                else if(!doi.equals(redirectedToUrl)) {
                    MDC.put("redirectedTo", redirectedToUrl);
                    log.debug("DOI is redirected");
                }

                // Validate URL
                Pattern p = Pattern.compile("^https?://([\\w\\.]*esrf.fr)/doi/([^/]+)/([^/#\\?]+)",
                                            Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(redirectedToUrl);
                boolean isSupported = m.matches();

                if(isSupported) {
                    this.authority = m.group(2);
                    this.recordId = m.group(3);
                    MDC.put("authority", this.authority);
                    MDC.put("recordId", this.recordId);
                    MDC.put("doiType", this.id);
                }

                return Uni.createFrom().item(Tuple2.of(isSupported, (ParserService)this));
            })
            .onFailure().invoke(e -> {
                log.error("Failed to check if DOI points to ESRF record");
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

        log.debug("Parse ESRF DOI");

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

                // Get the datasets
                return parser.getDataSetsAsync(sessionId.get(), this.authority, this.recordId);
            })
            .onItem().transformToMulti(datasets -> {
                // Got dataset(s)
                MDC.put("datasetCount", datasets.size());
                log.info("Found datasets");

                return Multi.createFrom().iterable(datasets);
            })
            .onItem().transformToUniAndConcatenate(dataset -> {
                // Got a dataset
                MDC.put("datasetId", dataset.id);
                MDC.put("datasetName", dataset.name);
                log.infof("Got dataset");

                // Fetch the files in the dataset
                return parser.getDataFilesAsync(sessionId.get(), dataset.id);
            })
            .onFailure().invoke(e -> {
                log.error("Failed to parse ESRF DOI");
            })
            .collect()
            .in(StorageContent::new, (sc, files) -> {
                // Got the files from a dataset
                MDC.put("fileCount", files.size());
                log.info("Got dataset files");

                var session = sessionId.get();
                for(var file : files) {
                    sc.elements.add(new StorageElement(file, baseUrl, session));
                }
            })
            .chain(sc -> {
                sc.count = sc.elements.size();
                return Uni.createFrom().item(sc);
            });

        return result;
    }

}
