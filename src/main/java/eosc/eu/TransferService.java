package eosc.eu;

import egi.fts.model.ObjectInfo;
import io.smallrye.mutiny.Uni;

import java.util.List;
import javax.ws.rs.core.Response;

import eosc.eu.model.*;
import org.jboss.resteasy.reactive.RestHeader;


/***
 * Generic data transfer service abstraction
 */
public interface TransferService {

    /***
     * Initialize the service, avoids the need to inject configuration.
     * @return true on success.
     */
    public abstract boolean initService(ServicesConfig.TransferServiceConfig config);

    /***
     * Get the human-readable name of the service.
     * @return Name of the transfer service.
     */
    public abstract String getServiceName();

    /***
     * Translates name of a generic information field to the name specific to the transfer service.
     * @param genericFieldName is the name of a TransferInfoExtended field.
     * @return Name of the field specific to this transfer service, null if requested field not supported.
     */
    public abstract String translateTransferInfoFieldName(String genericFieldName);

    /**
     * Retrieve information about current user.
     * @param auth The access token needed to call the service.
     * @return API Response, wraps an ActionSuccess(UserInfo) or an ActionError entity
     */
    public abstract Uni<UserInfo> getUserInfo(String auth);

    /**
     * Initiate new transfer of multiple sets of files.
     * @param auth The access token needed to call the service.
     * @param transfer The details of the transfer (source and destination files, parameters).
     * @return API Response, wraps an ActionSuccess(TransferInfo) or an ActionError entity
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
     * @return API Response, wraps an ActionSuccess(TransferList) or an ActionError entity
     */
    public abstract Uni<TransferList> findTransfers(String auth, String fields, int limit,
                                                    String timeWindow, String stateIn,
                                                    String srcStorageElement, String dstStorageElement,
                                                    String delegationId, String voName, String userDN);

    /**
     * Request information about a transfer.
     * @param auth The access token needed to call the service.
     * @param jobId The ID of the transfer to request info about.
     * @return API Response, wraps an ActionSuccess(TransferInfoExtended) or an ActionError entity
     */
    public abstract Uni<TransferInfoExtended> getTransferInfo(String auth, String jobId);

    /**
     * Request specific field from information about a transfer.
     * @param auth The access token needed to call the service.
     * @param jobId The ID of the transfer to request info about.
     * @param fieldName The name of the TransferInfoExtended field to retrieve.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    public abstract Uni<Response> getTransferInfoField(String auth, String jobId, String fieldName);

    /**
     * Cancel a transfer.
     * @param auth The access token needed to call the service.
     * @param jobId The ID of the transfer to cancel.
     * @return API Response, wraps an ActionSuccess(TransferInfoExtended) or an ActionError entity
     */
    public abstract Uni<TransferInfoExtended> cancelTransfer(String auth, String jobId);

    /**
     * List all files and sub-folders in a folder.
     * @param auth The access token needed to call the service.
     * @param folderUrl The link to the folder to list content of.
     * @return API Response, wraps an ActionSuccess(StorageContent) or an ActionError entity
     */
    //public abstract Uni<StorageContent> listFolderContent(String auth, String folderUrl);

    /**
     * Get the details of a file or folder.
     * @param auth The access token needed to call the service.
     * @param seUrl The link to the file or folder to det details of.
     * @return API Response, wraps an ActionSuccess(StorageElement) or an ActionError entity
     */
    public abstract Uni<StorageElement> getStorageElementInfo(@RestHeader("Authorization") String auth, String seUrl);
}
