package egi.eu;

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

import eosc.eu.TransfersConfig.TransferServiceConfig;
import eosc.eu.TransferService;
import eosc.eu.TransferServiceException;
import eosc.eu.model.*;
import egi.fts.FileTransferService;
import egi.fts.model.*;


/***
 * Class for working with EGI Data Transfer
 */
public class EgiDataTransfer implements TransferService {

    private static final Logger LOG = Logger.getLogger(EgiDataTransfer.class);
    private static Set<String> infoFieldsAsIs;
    private static Map<String, String> infoFieldsRenamed;

    private String name;
    private static FileTransferService fts;
    private int timeout;


    /***
     * Constructor
     */
    public EgiDataTransfer() {}

    /***
     * Initialize the REST client for the File Transfer Service that powers EGI Data Transfer
     * @return true on success
     */
    @PostConstruct
    public boolean initService(TransferServiceConfig serviceConfig) {

        this.name = serviceConfig.name();
        this.timeout = serviceConfig.timeout();

        if (null != this.fts)
            return true;

        LOG.debug("Obtaining REST client for File Transfer Service");

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
     * Signal if browsing the destination is supported
     * @param destination The key of the destination storage type from the configuration file
     * @return true if creating and managing storage elements is supported in associated destination storage(s)
     */
    public boolean canBrowseStorage(String destination) {

        if(destination.equals(Transfer.Destination.dcache))
            return true;
        else if(destination.equals(Transfer.Destination.ftp))
            return true;
        else if(destination.equals(Transfer.Destination.s3))
            return true;

        return false;
    }

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
            infoFieldsRenamed.put("finishedAt", "job_finished");
            infoFieldsRenamed.put("submittedAt", "submit_time");
            infoFieldsRenamed.put("submittedTo", "submit_host");
            infoFieldsRenamed.put("status", "http_status");
        }

        return infoFieldsRenamed.get(genericFieldName);
    }

    /**
     * Retrieve information about current user.
     * @param auth The access token that authorizes calling the service.
     * @return User information.
     */
    public Uni<eosc.eu.model.UserInfo> getUserInfo(String auth) {
        if(null == this.fts)
            return Uni.createFrom().failure(new TransferServiceException("invalidConfig"));

        Uni<eosc.eu.model.UserInfo> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("getUserInfoTimeout"))
            .chain(unused -> {
                // Get user info
                return this.fts.getUserInfoAsync(auth);
            })
            .chain(userInfo -> {
                // Got user info
                return Uni.createFrom().item(new eosc.eu.model.UserInfo(userInfo));
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Initiate new transfer of multiple sets of files.
     * @param auth The access token that authorizes calling the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param transfer The details of the transfer (source and destination files, parameters).
     * @return Identification for the new transfer.
     */
    public Uni<TransferInfo> startTransfer(String auth, String storageAuth, Transfer transfer) {
        if(null == this.fts)
            return Uni.createFrom().failure(new TransferServiceException("invalidConfig"));

        Uni<TransferInfo> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("startTransferTimeout"))
            .chain(unused -> {
                // If authentication info is provided for the storage, embed it in each destination URL
                if(null != storageAuth && !storageAuth.isBlank()) {
                    // TODO: Embed storage credentials in destination URLs
                }

                // Start new transfer
                return this.fts.startTransferAsync(auth, new Job(transfer));
            })
            .chain(jobInfo -> {
                // Transfer started
                return Uni.createFrom().item(new TransferInfo(jobInfo));
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
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
     * @param delegationId Filter by delegation ID of user who started the transfer
     * @param voName Filter by VO of user who started the transfer
     * @param userDN Filter by user who started the transfer
     * @return Matching transfers.
     */
    public Uni<TransferList> findTransfers(String auth,
                                           String fields, int limit,
                                           String timeWindow, @DefaultValue("ACTIVE") String stateIn,
                                           String srcStorageElement, String dstStorageElement,
                                           String delegationId, String voName, String userDN) {
        if(null == this.fts)
            return Uni.createFrom().failure(new TransferServiceException("invalidConfig"));

        // Translate field names
        String jobFields = null;
        if(null != fields && !fields.isEmpty()) {
            jobFields = "";
            String[] transferFields = fields.split(",");
            for (String tf : transferFields) {
                if(!jobFields.isEmpty())
                    jobFields += ",";

                String jf = this.translateTransferInfoFieldName(tf);
                if(null == jf)
                    // Found unsupported field
                    return Uni.createFrom().failure(new TransferServiceException("fieldNotSupported", Tuple2.of("fieldName", tf)));

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
                return this.fts.findTransfersAsync(auth, searchFields.get(), limit, timeWindow, stateIn,
                                                   srcStorageElement, dstStorageElement,
                                                   delegationId, voName, userDN);
            })
            .chain(jobs -> {
                // Got matching transfers
                return Uni.createFrom().item(new TransferList(jobs));
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Request information about a transfer.
     * @param auth The access token that authorizes calling the service.
     * @param jobId The ID of the transfer to request info about.
     * @return Details of the transfer.
     */
    public Uni<TransferInfoExtended> getTransferInfo(String auth, String jobId) {
        if(null == this.fts)
            return Uni.createFrom().failure(new TransferServiceException("invalidConfig"));

        Uni<TransferInfoExtended> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("getTransferInfoTimeout"))
            .chain(unused -> {
                // Get transfer info
                return this.fts.getTransferInfoAsync(auth, jobId);
            })
            .chain(jobInfoExt -> {
                // Got transfer info
                return Uni.createFrom().item(new TransferInfoExtended(jobInfoExt));
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
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
        if(null == this.fts)
            return Uni.createFrom().failure(new TransferServiceException("invalidConfig"));

        String jobFieldName = translateTransferInfoFieldName(fieldName);
        if(null == jobFieldName)
            return Uni.createFrom().failure(new TransferServiceException("fieldNotSupported"));

        Uni<Response> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("getTransferInfoFieldTimeout"))
            .chain(unused -> {
                // Get field value
                return this.fts.getTransferFieldAsync(auth, jobId, jobFieldName);
            })
            .chain(jobField -> {
                // Got field value
                Response response = Response.ok(jobField).build();

                // Determine if field is an object or a primitive type
                var entity = response.getEntity();
                String rawField = (null != entity) ? entity.toString() : "null";
                Pattern p = Pattern.compile("^\\{.+\\}$");
                Matcher m = p.matcher(rawField);
                if(!m.matches()) {
                    // Not an object, force return as text/plain
                    response = Response.ok(jobField).header(CONTENT_TYPE, MediaType.TEXT_PLAIN).build();
                }

                return Uni.createFrom().item(response);
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
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
        if(null == this.fts)
            return Uni.createFrom().failure(new TransferServiceException("invalidConfig"));

        Uni<TransferInfoExtended> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("cancelTransferTimeout"))
            .chain(unused -> {
                // Cancel transfer
                return this.fts.cancelTransferAsync(auth, jobId);
            })
            .chain(jobInfoExt -> {
                // Transfer canceled, got updated transfer info
                return Uni.createFrom().item(new TransferInfoExtended(jobInfoExt));
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * List all files and sub-folders in a folder.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUrl The link to the folder to list content of.
     * @return List of folder content.
     */
    public Uni<StorageContent> listFolderContent(String auth, String storageAuth, String folderUrl) {
        if(null == this.fts)
            return Uni.createFrom().failure(new TransferServiceException("invalidConfig"));

        Uni<StorageContent> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("listFolderContentTimeout"))
            .chain(unused -> {
                // List folder content
                return this.fts.listFolderContentAsync(auth, folderUrl);
            })
            .chain(contentList -> {
                // Got folder listing
                return Uni.createFrom().item(new StorageContent(folderUrl, contentList));
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Get the details of a file or folder.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param seUrl The link to the file or folder to det details of.
     * @return Details about the storage element.
     */
    public Uni<StorageElement> getStorageElementInfo(String auth, String storageAuth, String seUrl) {
        if(null == this.fts)
            return Uni.createFrom().failure(new TransferServiceException("invalidConfig"));

        Uni<StorageElement> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("getStorageElementInfoTimeout"))
            .chain(unused -> {
                // Get object info
                return this.fts.getObjectInfoAsync(auth, seUrl);
            })
            .chain(objInfo -> {
                // Got object info
                objInfo.objectUrl = seUrl;
                return Uni.createFrom().item(new StorageElement(objInfo));
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Create new folder.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUrl The link to the folder to create.
     * @return Confirmation message.
     */
    public Uni<String> createFolder(String auth, String storageAuth, String folderUrl) {
        if(null == this.fts)
            return Uni.createFrom().failure(new TransferServiceException("invalidConfig"));

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("createFolderTimeout"))
            .chain(unused -> {
                // Create folder
                var operation = new ObjectOperation(folderUrl);
                return this.fts.createFolderAsync(auth, operation);
            })
            .chain(code -> {
                // Got success code
                return Uni.createFrom().item(code);
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Delete existing folder.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUrl The link to the folder to delete.
     * @return Confirmation message.
     */
    public Uni<String> deleteFolder(String auth, String storageAuth, String folderUrl) {
        if(null == this.fts)
            return Uni.createFrom().failure(new TransferServiceException("invalidConfig"));

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("deleteFolderTimeout"))
            .chain(unused -> {
                // Delete folder
                var operation = new ObjectOperation(folderUrl);
                return this.fts.deleteFolderAsync(auth, operation);
            })
            .chain(code -> {
                // Got success code
                return Uni.createFrom().item(code);
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Delete existing file.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param fileUrl The link to the file to delete.
     * @return Confirmation message.
     */
    public Uni<String> deleteFile(String auth, String storageAuth, String fileUrl) {
        if(null == this.fts)
            return Uni.createFrom().failure(new TransferServiceException("invalidConfig"));

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("deleteFileTimeout"))
            .chain(unused -> {
                // Delete file
                var operation = new ObjectOperation(fileUrl);
                return this.fts.deleteFileAsync(auth, operation);
            })
            .chain(code -> {
                // Got success code
                return Uni.createFrom().item(code);
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Rename a folder or file.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param seOld The link to the storage element to rename.
     * @param seNew The link to the new name/location of the storage element.
     * @return Confirmation message.
     */
    public Uni<String> renameStorageElement(String auth, String storageAuth, String seOld, String seNew) {
        if(null == this.fts)
            throw new TransferServiceException("invalidConfig");

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("renameStorageElementTimeout"))
            .chain(unused -> {
                // Rename storage element
                var operation = new ObjectOperation(seOld, seNew);
                return this.fts.renameObjectAsync(auth, operation);
            })
            .chain(code -> {
                // Got success code
                return Uni.createFrom().item(code);
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }
}
