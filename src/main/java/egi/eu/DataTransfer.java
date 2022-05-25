package egi.eu;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.logging.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.HttpHeaders.*;

import eosc.eu.ServicesConfig;
import eosc.eu.TransferService;
import eosc.eu.TransferServiceException;
import eosc.eu.model.*;
import egi.fts.FileTransferService;
import egi.fts.model.*;


/***
 * Class for working with EGI Data Transfer
 */
public class DataTransfer implements TransferService {

    private static final Logger LOG = Logger.getLogger(DataTransfer.class);
    private static Set<String> infoFieldsAsIs;
    private static Map<String, String> infoFieldsRenamed;

    private String name;
    private FileTransferService fts;
    private int timeout;


    /***
     * Constructor
     */
    public DataTransfer() {}

    /***
     * Initialize the REST client for the File Transfer Service that powers EGI Data Transfer
     * @return true on success
     */
    @PostConstruct
    public boolean initService(ServicesConfig.TransferServiceConfig serviceConfig) {

        LOG.info("Obtaining REST client for EGI Data Transfer service");

        if (null != this.fts)
            return true;

        this.name = serviceConfig.name();
        this.timeout = serviceConfig.timeout();

        // Check if transfer service base URL is valid
        URL urlTransferService;
        try {
            urlTransferService = new URL(serviceConfig.url());
        } catch (MalformedURLException e) {
            LOG.error(e.getMessage());
            return false;
        }

        try {
            // Create the REST client for the transfer service
            this.fts = RestClientBuilder.newBuilder()
                        .baseUrl(urlTransferService)
                        .build(FileTransferService.class);

            return true;
        }
        catch (RestClientDefinitionException e) {
            LOG.error(e.getMessage());
        }

        return false;
    }

    /***
     * Get the human-readable name of the service.
     * @return Name of the transfer service.
     */
    public String getServiceName() { return this.name; }

    /***
     * Translates name of a generic information field to the name specific to the transfer service.
     * @param genericFieldName is the name of a TransferInfoExtended field.
     * @return Name of the field specific to this transfer service, null if requested field not supported.
     */
    public String translateTransferInfoFieldName(String genericFieldName) {

        if(null == infoFieldsAsIs) {
            infoFieldsAsIs = new HashSet<>(Arrays.asList(
                    "source_se",
                    "source_space_token",
                    "priority",
                    "retry",
                    "reason",
                    "vo_name",
                    "user_dn",
                    "cred_id"));
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
            infoFieldsRenamed.put("destination_space_token", "space_token");
            infoFieldsRenamed.put("verifyChecksum", "verify_checksum");
            infoFieldsRenamed.put("overwrite", "overwrite_flag");
            infoFieldsRenamed.put("retryDelay", "retry_delay");
            infoFieldsRenamed.put("maxTimeInQueue", "max_time_in_queue");
            infoFieldsRenamed.put("copyPinLifetime", "copy_pin_lifetime");
            infoFieldsRenamed.put("bringOnline", "bring_online");
            infoFieldsRenamed.put("targetQOS", "target_qos");
            infoFieldsRenamed.put("cancel", "cancel_job");
            infoFieldsRenamed.put("submittedAt", "submit_time");
            infoFieldsRenamed.put("submittedTo", "submit_host");
            infoFieldsRenamed.put("status", "http_status");
        }

        return infoFieldsRenamed.get(genericFieldName);
    }

    /**
     * Retrieve information about current user.
     * @param auth The access token that authorizes calling the service.
     * @return API Response, wraps an ActionSuccess(UserInfo) or an ActionError entity
     */
    public Uni<eosc.eu.model.UserInfo> getUserInfo(String auth) {
        if(null == this.fts)
            throw new TransferServiceException("invalidConfig");

        AtomicReference<Uni<eosc.eu.model.UserInfo>> result = new AtomicReference<>();

        var userInfo = this.fts.getUserInfoAsync(auth);
        userInfo
            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("getUserInfoTimeout"))
            .chain(ui -> {
                // Got user info
                result.set(Uni.createFrom().item(new eosc.eu.model.UserInfo(ui)));
                return Uni.createFrom().nullItem();
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
                result.set(Uni.createFrom().failure(e));
            })
            .await().indefinitely();

        return result.get();
    }

    /**
     * Initiate new transfer of multiple sets of files.
     * @param auth The access token that authorizes calling the service.
     * @param transfer The details of the transfer (source and destination files, parameters).
     * @return API Response, wraps an ActionSuccess(TransferInfo) or an ActionError entity
     */
    public Uni<TransferInfo> startTransfer(String auth, Transfer transfer) {
        if(null == this.fts)
            throw new TransferServiceException("invalidConfig");

        AtomicReference<Uni<TransferInfo>> result = new AtomicReference<>();

        var transferInfo = this.fts.startTransferAsync(auth, new Job(transfer));
        transferInfo
            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("startTransferTimeout"))
            .chain(jobInfo -> {
                // Transfer started
                result.set(Uni.createFrom().item(new TransferInfo(jobInfo)));
                return Uni.createFrom().nullItem();
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
                result.set(Uni.createFrom().failure(e));
            })
            .await().indefinitely();

        return result.get();
    }

    /**
     * Request information about a transfer.
     * @param auth The access token that authorizes calling the service.
     * @param jobId The ID of the transfer to request info about.
     * @return API Response, wraps an ActionSuccess(TransferInfoExtended) or an ActionError entity
     */
    public Uni<TransferInfoExtended> getTransferInfo(String auth, String jobId) {
        if(null == this.fts)
            throw new TransferServiceException("invalidConfig");

        AtomicReference<Uni<TransferInfoExtended>> result = new AtomicReference<>();

        var transferInfo = this.fts.getTransferInfoAsync(auth, jobId);
        transferInfo
            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("getTransferInfoTimeout"))
            .chain(jobInfoExt -> {
                // Got transfer info
                result.set(Uni.createFrom().item(new TransferInfoExtended(jobInfoExt)));
                return Uni.createFrom().nullItem();
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
                result.set(Uni.createFrom().failure(e));
            })
            .await().indefinitely();

        return result.get();
    }

    /**
     * Request specific field from information about a transfer.
     * @param auth The access token that authorizes calling the service.
     * @param jobId The ID of the transfer to request info about.
     * @param fieldName The name of the TransferInfoExtended field to retrieve.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    public Uni<Response> getTransferInfoField(String auth, String jobId, String fieldName) {
        if(null == this.fts)
            throw new TransferServiceException("invalidConfig");

        AtomicReference<Uni<Response>> result = new AtomicReference<>();

        String jobFieldName = translateTransferInfoFieldName(fieldName);
        if(null == jobFieldName)
            throw new TransferServiceException("fieldNotFound");

        var field = this.fts.getTransferFieldAsync(auth, jobId, jobFieldName);
        field
            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("getTransferInfoFieldTimeout"))
            .chain(jobField -> {
                // Got field value
                Response response = Response.ok(jobField).build();

                // Determine if field is an object or a primitive type
                var entity = response.getEntity();
                String rawField = (null != entity) ? entity.toString() : "null";
                Pattern p = Pattern.compile("^\\{.+\\}$", Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(rawField);
                if(!m.matches()) {
                    // Not an object
                    // Force return as text/plain
                    response = Response.ok(jobField).header(CONTENT_TYPE, MediaType.TEXT_PLAIN).build();
                }

                result.set(Uni.createFrom().item(response));
                return Uni.createFrom().nullItem();
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
                result.set(Uni.createFrom().failure(e));
            })
            .await().indefinitely();

        return result.get();
    }
}
