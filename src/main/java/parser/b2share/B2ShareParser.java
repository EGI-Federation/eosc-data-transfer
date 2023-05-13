package parser.b2share;

import eosc.eu.PortConfig;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

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
 * Class for parsing B2Share DOIs
 */
public class B2ShareParser implements ParserService {

    private static final Logger log = Logger.getLogger(B2ShareParser.class);

    private String id;
    private String name;
    private URL urlServer;
    private String recordId;
    private int timeout;
    private B2Share parser;


    /***
     * Constructor
     * @param id The key of the parser in the config file
     */
    public B2ShareParser(String id) { this.id = id; }

    /***
     * Initialize the REST client for B2Share
     * @param config Configuration of the parser, from the config file.
     * @param port The port on which the application runs, from the config file.
     * @return true on success
     */
    public boolean init(ParserConfig config, PortConfig port) {
        this.name = config.name();
        this.timeout = config.timeout();

        if (null != this.parser)
            return true;

        if(null == this.urlServer) {
            log.error("Missing B2Share server, call canParseDOI() first");
            return false;
        }

        log.debugf("Obtaining REST client for B2Share server %s", this.urlServer);

        try {
            // Create the REST client for the parser service
            this.parser = RestClientBuilder.newBuilder()
                            .baseUrl(this.urlServer)
                            .build(B2Share.class);

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

        log.debug("Check if DOI points to B2Share record");

        boolean isValid = null != doi && !doi.isBlank();
        if(!isValid)
            return Uni.createFrom().failure(new TransferServiceException("doiInvalid"));

        // Check if DOI points/redirects to a B2Share record
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
                Pattern p = Pattern.compile("^(https?://[^/:]*b2share[^/:]*:?[\\d]*)/records/(.+)",
                                            Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(redirectedToUrl);
                boolean isSupported = m.matches();

                if(isSupported) {
                    this.recordId = m.group(2);
                    try {
                        this.urlServer = new URL(m.group(1));
                        MDC.put("urlServer", this.urlServer);
                        MDC.put("recordId", this.recordId);
                        MDC.put("doiType", this.id);
                    } catch (MalformedURLException e) {
                        log.error(e.getMessage());
                        isSupported = false;
                    }
                }

                return Uni.createFrom().item(Tuple2.of(isSupported, (ParserService)this));
            })
            .onFailure().invoke(e -> {
                log.error("Failed to check if DOI points to B2Share record");
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

        log.debug("Parse B2Share DOI");

        if(null == this.parser)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        if(null == this.recordId || this.recordId.isEmpty())
            return Uni.createFrom().failure(new TransferServiceException("noRecordId"));

        Uni<StorageContent> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("doiParseTimeout"))
            .chain(unused -> {
                // Get B2Share record details
                return this.parser.getRecordAsync(this.recordId);
            })
            .chain(record -> {
                // Got B2Share record
                log.info("Got B2Share record");

                // Get bucket that holds the files
                String linkToFiles = (null != record.links) ? record.links.get("files") : null;
                if(null != linkToFiles) {
                    Pattern p = Pattern.compile("^https?://[^/:]+/api/files/(.+)", Pattern.CASE_INSENSITIVE);
                    Matcher m = p.matcher(linkToFiles);
                    if(m.matches()) {
                        // Get files in the bucket
                        var bucket = m.group(1);
                        return this.parser.getFilesInBucketAsync(bucket);
                    }
                }

                return Uni.createFrom().failure(new TransferServiceException("noFilesLink"));
            })
            .chain(bucket -> {
                // Got bucket content
                log.info("Got B2Share bucket");

                // Build list of source files
                StorageContent srcFiles = new StorageContent(bucket.contents.size());
                for(var file : bucket.contents) {
                    srcFiles.elements.add(new StorageElement(file));
                }

                srcFiles.count = srcFiles.elements.size();

                // Success
                return Uni.createFrom().item(srcFiles);
            })
            .onFailure().invoke(e -> {
                log.errorf("Failed to parse B2Share DOI %s", doi);
            });

        return result;
    }

}
