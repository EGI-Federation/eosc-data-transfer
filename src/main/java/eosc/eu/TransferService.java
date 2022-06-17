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
     * @return true if creating and managing storage elements is supported in associated destination storage(s)
     */
    public abstract boolean canBrowseStorage();

    /***
     * Translates name of a generic information field to the name specific to the transfer service.
     * @param genericFieldName is the name of a TransferInfoExtended field.
     * @return Name of the field specific to this transfer service, null if requested field not supported.
     */
    public abstract String translateTransferInfoFieldName(String genericFieldName);

    /**
     * Retrieve information about current user.
     * @param auth The access token needed to call the service.
     * @return User information.
     */
    public abstract Uni<UserInfo> getUserInfo(String auth);

    /**
     * Initiate new transfer of multiple sets of files.
     * @param auth The access token needed to call the service.
     * @param transfer The details of the transfer (source and destination files, parameters).
     * @return Identification for the new transfer.
     */
    public abstract Uni<TransferInfo> startTransfer(String auth, Transfer transfer);

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
    public abstract Uni<TransferList> findTransfers(String auth, String fields, int limit,
                                                    String timeWindow, String stateIn,
                                                    String srcStorageElement, String dstStorageElement,
                                                    String delegationId, String voName, String userDN);

    /**
     * Request information about a transfer.
     * @param auth The access token needed to call the service.
     * @param jobId The ID of the transfer to request info about.
     * @return Details of the transfer.
     */
    public abstract Uni<TransferInfoExtended> getTransferInfo(String auth, String jobId);

    /**
     * Request specific field from information about a transfer.
     * @param auth The access token needed to call the service.
     * @param jobId The ID of the transfer to request info about.
     * @param fieldName The name of the TransferInfoExtended field to retrieve.
     * @return The value of the requested field from a transfer's information.
     */
    public abstract Uni<Response> getTransferInfoField(String auth, String jobId, String fieldName);

    /**
     * Cancel a transfer.
     * @param auth The access token needed to call the service.
     * @param jobId The ID of the transfer to cancel.
     * @return Details of the cancelled transfer.
     */
    public abstract Uni<TransferInfoExtended> cancelTransfer(String auth, String jobId);

    /**
     * List all files and sub-folders in a folder.
     * @param auth The access token needed to call the service.
     * @param folderUrl The link to the folder to list content of.
     * @return List of the folder content.
     */
    public abstract Uni<StorageContent> listFolderContent(String auth, String folderUrl);

    /**
     * Get the details of a file or folder.
     * @param auth The access token needed to call the service.
     * @param seUrl The link to the file or folder to det details of.
     * @return Details about the storage element.
     */
    public abstract Uni<StorageElement> getStorageElementInfo(String auth, String seUrl);

    /**
     * Create new folder.
     * @param auth The access token needed to call the service.
     * @param folderUrl The link to the folder to create.
     * @return Confirmation message.
     */
    public abstract Uni<String> createFolder(String auth, String folderUrl);

    /**
     * Delete existing folder.
     * @param auth The access token needed to call the service.
     * @param folderUrl The link to the folder to delete.
     * @return Confirmation message.
     */
    public abstract Uni<String> deleteFolder(String auth, String folderUrl);

    /**
     * Delete existing file.
     * @param auth The access token needed to call the service.
     * @param fileUrl The link to the file to delete.
     * @return Confirmation message.
     */
    public abstract Uni<String> deleteFile(String auth, String fileUrl);

    /**
     * Rename a folder or file.
     * @param auth The access token needed to call the service.
     * @param seOld The link to the storage element to rename.
     * @param seNew The link to the new name/location of the storage element.
     * @return Confirmation message.
     */
    public abstract Uni<String> renameStorageElement(String auth, String seOld, String seNew);
}
