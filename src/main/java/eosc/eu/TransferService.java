package eosc.eu;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;

import eosc.eu.model.*;
import eosc.eu.TransfersStoragesConfig.TransferServiceConfig;


/***
 * Generic data transfer service abstraction
 */
public interface TransferService {

    /***
     * Initialize the service, avoids the need to inject configuration.
     * @param config Service configuration loaded from the config file
     * @return true on success.
     */
    boolean initService(TransferServiceConfig config);

    /***
     * Get the human-readable name of the service.
     * @return Name of the transfer service.
     */
    String getServiceName();

    /***
     * Translates name of a generic information field to the name specific to the transfer service.
     * @param genericFieldName is the name of a TransferInfoExtended field.
     * @return Name of the field specific to this transfer service, null if requested field not supported.
     */
    String translateTransferInfoFieldName(String genericFieldName);

    /**
     * Retrieve information about current user.
     * @param tsAuth The access token needed to call the service.
     * @return User information.
     */
    Uni<UserInfo> getUserInfo(String tsAuth);

    /**
     * Initiate new transfer of multiple sets of files.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param transfer The details of the transfer (source and destination files, parameters).
     * @return Identification for the new transfer.
     */
    Uni<TransferInfo> startTransfer(String tsAuth, String storageAuth, Transfer transfer);

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
    Uni<TransferList> findTransfers(String tsAuth, String fields, int limit,
                                    String timeWindow, String stateIn,
                                    String srcStorageElement, String dstStorageElement,
                                    String delegationId, String voName, String userDN);

    /**
     * Request information about a transfer.
     * @param tsAuth The access token needed to call the service.
     * @param jobId The ID of the transfer to request info about.
     * @return Details of the transfer.
     */
    Uni<TransferInfoExtended> getTransferInfo(String tsAuth, String jobId);

    /**
     * Request specific field from information about a transfer.
     * @param tsAuth The access token needed to call the service.
     * @param jobId The ID of the transfer to request info about.
     * @param fieldName The name of the TransferInfoExtended field to retrieve.
     * @return The value of the requested field from a transfer's information.
     */
    Uni<Response> getTransferInfoField(String tsAuth, String jobId, String fieldName);

    /**
     * Cancel a transfer.
     * @param tsAuth The access token needed to call the service.
     * @param jobId The ID of the transfer to cancel.
     * @return Details of the cancelled transfer.
     */
    Uni<TransferInfoExtended> cancelTransfer(String tsAuth, String jobId);

}
