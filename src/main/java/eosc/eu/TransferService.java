package eosc.eu;

import io.smallrye.mutiny.Uni;
import javax.ws.rs.core.Response;

import eosc.eu.model.*;
import eosc.eu.TransfersConfig.TransferServiceConfig;


/***
 * Generic data transfer service abstraction
 */
public interface TransferService {

    /***
     * Initialize the service, avoids the need to inject configuration.
     * @return true on success.
     */
    public abstract boolean initService(TransferServiceConfig config);

    /***
     * Get the human-readable name of the service.
     * @return Name of the transfer service.
     */
    public abstract String getServiceName();

    /***
     * Signal if this browsing the destination is supported
     * @param destination The key of the destination storage type from the configuration file
     * @return true if creating and managing storage elements is supported in associated destination storage(s)
     */
    public abstract boolean canBrowseStorage(String destination);

    /***
     * Translates name of a generic information field to the name specific to the transfer service.
     * @param genericFieldName is the name of a TransferInfoExtended field.
     * @return Name of the field specific to this transfer service, null if requested field not supported.
     */
    public abstract String translateTransferInfoFieldName(String genericFieldName);

    /**
     * Retrieve information about current user.
     * @param tsAuth The access token needed to call the service.
     * @return User information.
     */
    public abstract Uni<UserInfo> getUserInfo(String tsAuth);

    /**
     * Initiate new transfer of multiple sets of files.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param transfer The details of the transfer (source and destination files, parameters).
     * @return Identification for the new transfer.
     */
    public abstract Uni<TransferInfo> startTransfer(String tsAuth, String storageAuth, Transfer transfer);

    /***
     * Find transfers matching criteria.
     * @param tsAuth The access token needed to call the service.
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
    public abstract Uni<TransferList> findTransfers(String tsAuth, String fields, int limit,
                                                    String timeWindow, String stateIn,
                                                    String srcStorageElement, String dstStorageElement,
                                                    String delegationId, String voName, String userDN);

    /**
     * Request information about a transfer.
     * @param tsAuth The access token needed to call the service.
     * @param jobId The ID of the transfer to request info about.
     * @return Details of the transfer.
     */
    public abstract Uni<TransferInfoExtended> getTransferInfo(String tsAuth, String jobId);

    /**
     * Request specific field from information about a transfer.
     * @param tsAuth The access token needed to call the service.
     * @param jobId The ID of the transfer to request info about.
     * @param fieldName The name of the TransferInfoExtended field to retrieve.
     * @return The value of the requested field from a transfer's information.
     */
    public abstract Uni<Response> getTransferInfoField(String tsAuth, String jobId, String fieldName);

    /**
     * Cancel a transfer.
     * @param tsAuth The access token needed to call the service.
     * @param jobId The ID of the transfer to cancel.
     * @return Details of the cancelled transfer.
     */
    public abstract Uni<TransferInfoExtended> cancelTransfer(String tsAuth, String jobId);

    /**
     * List all files and sub-folders in a folder.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUrl The link to the folder to list content of.
     * @return List of the folder content.
     */
    public abstract Uni<StorageContent> listFolderContent(String tsAuth, String storageAuth, String folderUrl);

    /**
     * Get the details of a file or folder.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param seUrl The link to the file or folder to det details of.
     * @return Details about the storage element.
     */
    public abstract Uni<StorageElement> getStorageElementInfo(String tsAuth, String storageAuth, String seUrl);

    /**
     * Create new folder.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUrl The link to the folder to create.
     * @return Confirmation message.
     */
    public abstract Uni<String> createFolder(String tsAuth, String storageAuth, String folderUrl);

    /**
     * Delete existing folder.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUrl The link to the folder to delete.
     * @return Confirmation message.
     */
    public abstract Uni<String> deleteFolder(String tsAuth, String storageAuth, String folderUrl);

    /**
     * Delete existing file.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param fileUrl The link to the file to delete.
     * @return Confirmation message.
     */
    public abstract Uni<String> deleteFile(String tsAuth, String storageAuth, String fileUrl);

    /**
     * Rename a folder or file.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param seOld The link to the storage element to rename.
     * @param seNew The link to the new name/location of the storage element.
     * @return Confirmation message.
     */
    public abstract Uni<String> renameStorageElement(String tsAuth, String storageAuth, String seOld, String seNew);
}
