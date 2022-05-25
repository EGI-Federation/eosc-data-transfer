package eosc.eu;

import io.smallrye.mutiny.Uni;
import javax.ws.rs.core.Response;

import eosc.eu.model.*;


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
}
