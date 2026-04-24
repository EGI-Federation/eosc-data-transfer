package egi.eu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.runtime.TokensHelper;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import cern.model.*;
import cern.FileTransferService;

import static jakarta.ws.rs.core.HttpHeaders.*;
import static eosc.eu.Utils.loadKeyStore;

import cern.FileTransferServiceException;
import eosc.eu.model.*;
import eosc.eu.TransferService;
import eosc.eu.TransferServiceException;
import eosc.eu.DataStorageCredentials;
import eosc.eu.model.TransferInfoExtended.TransferState;
import eosc.eu.TransferConfig.TransferServiceConfig;
import eosc.eu.model.TransferPayloadInfo.FileDetails;


/***
 * Class for working with EGI Data Transfer
 */
public class EgiDataTransfer implements TransferService {

    private static final Logger log = Logger.getLogger(EgiDataTransfer.class);
    private static Set<String> infoFieldsAsIs;
    private static Map<String, String> infoFieldsRenamed;

    private String name;
    private String url;
    private FileTransferService fts; // REST client used for transfers
    private int timeout;

    @Inject
    OidcClient client;
    TokensHelper tokenHelper;

    private final ObjectMapper objectMapper;


    /***
     * Constructor
     */
    public EgiDataTransfer() {
        this.tokenHelper = new TokensHelper();
        this.objectMapper = new ObjectMapper();
    }

    /***
     * Initialize the REST client for the File Transfer Service that powers EGI Data Transfer
     * @param serviceConfig Configuration loaded from the config file
     * @return true on success
     */
    @PostConstruct
    public boolean initService(TransferServiceConfig serviceConfig) {

        this.name = serviceConfig.name();
        this.timeout = serviceConfig.timeout();

        if(null != fts)
            return true;

        MDC.put("serviceUrl", serviceConfig.url());
        log.debug("Obtaining REST client for File Transfer Service");

        // Check if transfer service base URL is valid
        URL serviceUrl = null;
        try {
            serviceUrl = new URL(serviceConfig.url());
        } catch (MalformedURLException e) {
            log.error(e.getMessage());
            return false;
        }

        this.url = serviceUrl.getProtocol() + "://" + serviceUrl.getHost();

        try {
            // Create the REST client for the transfer service
            var tsFile = serviceConfig.trustStoreFile().isPresent() ?
                         serviceConfig.trustStoreFile().get() : "";
            var tsPass = serviceConfig.trustStorePassword().isPresent() ?
                         serviceConfig.trustStorePassword().get() : "";
            var ots = loadKeyStore(tsFile, tsPass, log);
            var rcb = RestClientBuilder.newBuilder().baseUrl(serviceUrl);

            if(ots.isPresent())
                rcb.trustStore(ots.get());

            fts = rcb.build(FileTransferService.class);

            return true;
        }
        catch(RestClientDefinitionException | IllegalStateException e) {
            log.error(e.getMessage());
        }

        return false;
    }

    /***
     * Get the human-readable name of the service.
     * @return Name of the transfer service.
     */
    public String getServiceName() { return this.name; }

    /***
     * Get the base URL of the service.
     * @return URL to the transfer service.
     */
    public String getServiceUrl() { return this.url; }

    /***
     * Convert and store a generic transfer state to a service specific state.
     * @param state New state
     * @return Transfer state the service understands
     */
    private String transferStateToString(TransferState state) {
        String status = state.toString();

        switch(state) {
            case submitted ->  { status = "SUBMITTED"; }
            case active -> { status = "ACTIVE"; }
            case canceled -> { status = "CANCELED"; }
            case failed -> { status = "FAILED"; }
            case succeeded -> { status = "FINISHED"; }
            case partial -> { status = "FINISHEDDIRTY"; }
            default -> { status = status.toUpperCase(); }
        }

        return status;
    }

    /***
     * Translates name of a generic information field to the name specific to the transfer service.
     * @param genericFieldName is the name of a TransferInfoExtended field.
     * @return Name of the field specific to this transfer service, null if requested field not supported.
     */
    public String translateTransferInfoFieldName(String genericFieldName) {

        if(null == infoFieldsAsIs) {
            infoFieldsAsIs = new HashSet<>(Arrays.asList(
                    "source_space_token",
                    "priority",
                    "retry",
                    "reason"));
        }

        if(infoFieldsAsIs.contains(genericFieldName)) {
            // Field supported with the same name
            return genericFieldName;
        }

        if(null == infoFieldsRenamed) {
            infoFieldsRenamed = new HashMap<>();
            infoFieldsRenamed.put("jobId", "job_id");
            infoFieldsRenamed.put("jobState", "job_state");
            infoFieldsRenamed.put("jobType", "job_type");
            infoFieldsRenamed.put("jobMetadata", "job_metadata");
            infoFieldsRenamed.put("destination_se", "dest_se");
            infoFieldsRenamed.put("sourceSE", "source_se");
            infoFieldsRenamed.put("sourceSS", "source_ss");
            infoFieldsRenamed.put("destinationSS", "destination_ss");
            infoFieldsRenamed.put("destination_space_token", "space_token");
            infoFieldsRenamed.put("verifyChecksum", "verify_checksum");
            infoFieldsRenamed.put("overwrite", "overwrite_flag");
            infoFieldsRenamed.put("retryDelay", "retry_delay");
            infoFieldsRenamed.put("maxTimeInQueue", "max_time_in_queue");
            infoFieldsRenamed.put("copyPinLifetime", "copy_pin_lifetime");
            infoFieldsRenamed.put("bringOnline", "bring_online");
            infoFieldsRenamed.put("targetQoS", "target_qos");
            infoFieldsRenamed.put("cancel", "cancel_job");
            infoFieldsRenamed.put("finishedAt", "job_finished");
            infoFieldsRenamed.put("submittedAt", "submit_time");
            infoFieldsRenamed.put("submittedTo", "submit_host");
            infoFieldsRenamed.put("status", "http_status");
            infoFieldsRenamed.put("voName", "vo_name");
            infoFieldsRenamed.put("userId", "user_dn");
            infoFieldsRenamed.put("credId", "cred_id");
        }

        return infoFieldsRenamed.get(genericFieldName);
    }

    /**
     * Initiate new transfer of multiple sets of files.
     * @param auth The access token that authorizes calling the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param transfer The details of the transfer (source and destination files, parameters).
     * @return Identification for the new transfer.
     */
    public Uni<TransferInfo> startTransfer(String auth, String storageAuth, Transfer transfer) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<TransferInfo> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("startTransferTimeout"))
            .chain(s3ConfigResult -> {
                // Start new transfer
                Job job = new Job(transfer);
                if(null != storageAuth) {
                    DataStorageCredentials credentials = new DataStorageCredentials(storageAuth);
                    if(!credentials.isValid())
                        return Uni.createFrom().failure(new TransferServiceException("badRequest"));

                    job.params.s3_credentials = storageAuth;
                }
                return fts.startTransferAsync(auth, job);
            })
            .chain(jobInfo -> {
                // Transfer started
                MDC.put("jobId", jobInfo.job_id);
                return Uni.createFrom().item(new TransferInfo(jobInfo));
            })
            .onFailure().invoke(e -> {
                if(e instanceof FileTransferServiceException)
                    log.error(((FileTransferServiceException)e).errorDetail());
                else
                    log.error(e.getMessage());
            });

        return result;
    }

    /***
     * Find transfers matching criteria.
     * @param auth The access token needed to call the service.
     * @param fields Comma separated list of fields to return for each transfer
     * @param limit Maximum number of transfers to return
     * @param timeWindow For terminal states, limit results to 'hours[:minutes]' into the past
     * @param stateIn Comma separated list of job states to match, by default returns 'ACTIVE' only
     * @param srcStorageElement Source storage element
     * @param dstStorageElement Destination storage element
     * @param voName Filter by VO of user who started the transfer
     * @param userDN Filter by user who started the transfer
     * @return Matching transfers.
     */
    public Uni<TransferList> findTransfers(String auth,
                                           String fields, int limit,
                                           String timeWindow, TransferState stateIn,
                                           String srcStorageElement, String dstStorageElement,
                                           String voName, String userDN) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        // Translate field names
        String jobFields = null;
        if(null != fields && !fields.isEmpty()) {
            jobFields = "";
            String[] transferFields = fields.split(",");
            for (String tf : transferFields) {
                if(!jobFields.isEmpty())
                    jobFields += ",";

                String jf = this.translateTransferInfoFieldName(tf);
                if(null == jf) {
                    // Found unsupported field
                    MDC.put("fieldName", tf);
                    return Uni.createFrom().failure(new TransferServiceException("fieldNotSupported",
                                                                                 Tuple2.of("fieldName", tf)));
                }

                jobFields += jf;
            }
        }

        AtomicReference<String> searchFields = new AtomicReference<>(jobFields);
        Uni<TransferList> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("findTransfersTimeout"))
            .chain(unused -> {
                // List matching transfers
                return fts.findTransfersAsync(auth, searchFields.get(), limit, timeWindow,
                                              transferStateToString(stateIn),
                                              srcStorageElement, dstStorageElement,
                                              voName, userDN);
            })
            .chain(jobs -> {
                // Got matching transfers
                MDC.put("jobCount", jobs.size());
                return Uni.createFrom().item(new TransferList(jobs));
            })
            .onFailure().invoke(e -> {
                if(e instanceof FileTransferServiceException)
                    log.error(((FileTransferServiceException)e).errorDetail());
                else
                    log.error(e.getMessage());
            });

        return result;
    }

    /**
     * Request information about a transfer.
     * @param auth The access token that authorizes calling the service.
     * @param jobId The ID of the transfer to request info about.
     * @param fileInfo For which files to return transfer info.
     * @return Details of the transfer.
     */
    public Uni<TransferInfoExtended> getTransferInfo(String auth, String jobId, FileDetails fileInfo) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        var jobInfoExt = new AtomicReference<JobInfoExtended>(null);
        Uni<TransferInfoExtended> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("getTransferInfoTimeout"))
            .chain(unused -> {
                // Get transfer info
                return fts.getTransferInfoAsync(auth, jobId);
            })
            .chain(jobInfo -> {
                // Got transfer info
                jobInfoExt.set(jobInfo);

                if(FileDetails.none == fileInfo)
                    return Uni.createFrom().nullItem();

                // Get detailed status for each file in the transfer
                return fts.getTransferFilesAsync(auth, jobId);
            })
            .chain(jobFileInfos -> {
                // Got detailed status of each file, success
                var jobInfo = jobInfoExt.get();
                if(null != jobFileInfos)
                    jobInfo.file_info = Optional.of(jobFileInfos);

                return Uni.createFrom().item(new TransferInfoExtended(jobInfo, fileInfo));
            })
            .onFailure().invoke(e -> {
                if(e instanceof FileTransferServiceException)
                    log.error(((FileTransferServiceException)e).errorDetail());
                else
                    log.error(e.getMessage());
            });

        return result;
    }

    /**
     * Request specific field from information about a transfer.
     * @param auth The access token that authorizes calling the service.
     * @param jobId The ID of the transfer to request info about.
     * @param fieldName The name of the TransferInfoExtended field to retrieve.
     * @return The value of the requested field from a transfer's information.
     */
    public Uni<Response> getTransferInfoField(String auth, String jobId, String fieldName) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        String jobFieldName = translateTransferInfoFieldName(fieldName);
        if(null == jobFieldName)
            return Uni.createFrom().failure(new TransferServiceException("fieldNotSupported"));

        Uni<Response> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("getTransferInfoFieldTimeout"))
            .chain(unused -> {
                // Get field value
                return fts.getTransferFieldAsync(auth, jobId, jobFieldName);
            })
            .chain(jobField -> {
                // Got field value
                Response response = Response.ok(jobField).build();

                // Determine if field is an object or a primitive type
                var entity = response.getEntity();
                String rawField = (null != entity) ? entity.toString() : "null";
                Pattern p = Pattern.compile("^\\{.+\\}$");
                Matcher m = p.matcher(rawField);
                if(m.matches()) {
                    // It's an object
                    try {
                        MDC.put("fieldValue", this.objectMapper.writeValueAsString(jobField));
                    }
                    catch (JsonProcessingException e) {
                        return Uni.createFrom().failure(new TransferServiceException(e, "serialize"));
                    }
                }
                else {
                    // Not an object, force return as text/plain
                    MDC.put("fieldValue", jobField);
                    response = Response.ok(jobField).header(CONTENT_TYPE, MediaType.TEXT_PLAIN).build();
                }

                return Uni.createFrom().item(response);
            })
            .onFailure().invoke(e -> {
                if(e instanceof FileTransferServiceException)
                    log.error(((FileTransferServiceException)e).errorDetail());
                else
                    log.error(e.getMessage());
            });

        return result;
    }

    /**
     * Cancel a transfer.
     * @param auth The access token that authorizes calling the service.
     * @param jobId The ID of the transfer to cancel.
     * @return Details of the cancelled transfer.
     */
    public Uni<TransferInfoExtended> cancelTransfer(String auth, String jobId) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<TransferInfoExtended> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("cancelTransferTimeout"))
            .chain(unused -> {
                // Cancel transfer
                return fts.cancelTransferAsync(auth, jobId);
            })
            .chain(jobInfoExt -> {
                // Transfer canceled, got updated transfer info
                return Uni.createFrom().item(new TransferInfoExtended(jobInfoExt));
            })
            .onFailure().invoke(e -> {
                if(e instanceof FileTransferServiceException)
                    log.error(((FileTransferServiceException)e).errorDetail());
                else
                    log.error(e.getMessage());
            });

        return result;
    }

}
