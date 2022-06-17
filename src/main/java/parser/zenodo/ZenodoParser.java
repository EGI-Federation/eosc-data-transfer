package parser.zenodo;

import eosc.eu.ActionError;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestHeader;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.HttpHeaders.*;

import eosc.eu.ParsersConfig;
import eosc.eu.ParsersConfig.ParserConfig;
import eosc.eu.ParserService;
import eosc.eu.TransferServiceException;
import eosc.eu.model.*;



/***
 * Class for parsing Zenodo DOIs
 */
public class ZenodoParser implements ParserService {

    private static final Logger LOG = Logger.getLogger(ZenodoParser.class);

    private String name;
    private int timeout;
    private String recordId;
    private static Zenodo parser;


    /***
     * Constructor
     */
    public ZenodoParser() {}

    /***
     * Initialize the REST client for Zenodo
     * @return true on success
     */
    @PostConstruct
    public boolean initParser(ParserConfig serviceConfig) {

        this.name = serviceConfig.name();
        this.timeout = serviceConfig.timeout();

        if (null != this.parser)
            return true;

        LOG.debug("Obtaining REST client for Zenodo");

        // Check if base URL is valid
        URL urlParserService;
        try {
            urlParserService = new URL(serviceConfig.url());
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
     * Get the human-readable name of the parser.
     * @return Name of the parser service.
     */
    public String getParserName() { return this.name; }

    /***
     * Get the Id of the source record.
     * @return Source record Id.
     */
    public String getSourceId() { return this.recordId; }

    /***
     * Checks if the parser service understands this DOI.
     * @param doi The DOI for a data set.
     * @return Return true if the parser service can parse this DOI.
     */
    public boolean canParseDOI(String doi) {
        if(null == this.parser)
            return false;

        // Validate DOI
        boolean isValid = null != doi && !doi.isEmpty();
        if(isValid) {
            Pattern p = Pattern.compile("^https?://([\\w\\.]+)/([\\w\\.]+)/zenodo\\.(\\d+).*", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(doi);
            isValid = m.matches();

            this.recordId = isValid ? m.group(3) : null;
        }

        return isValid;
    }

    /**
     * Parse the DOI and return a set of files in the data set.
     * @param auth The access token needed to call the service.
     * @param doi The DOI for a data set.
     * @return List of files in the data set.
     */
    public Uni<StorageContent> parseDOI(String auth, String doi) {
        if(null == this.parser)
            return Uni.createFrom().failure(new TransferServiceException("invalidConfig"));

        if(null == this.recordId || this.recordId.isEmpty())
            return Uni.createFrom().failure(new TransferServiceException("noRecordId"));

        Uni<StorageContent> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("parseDOITimeout"))
            .chain(unused -> {
                // Get Zenodo record details
                return this.parser.getRecordsAsync(this.recordId);
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
                LOG.error(e);
            });

        return result;
    }

}
