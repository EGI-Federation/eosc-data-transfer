package eosc.eu;

import io.smallrye.mutiny.Uni;

import eosc.eu.model.*;
import eosc.eu.TransferConfig.StorageSystemConfig;
import io.vertx.mutiny.core.Vertx;


/***
 * Generic data storage service abstraction
 */
public interface StorageService {

    /***
     * Initialize the service, avoids the need to inject configuration.
     * @param config Service configuration loaded from the config file.
     * @param vertx Vertx service in case you need custom clients.
     * @param storageElementUri the URI to a folder or file on the storage system.
     * @param storageAuth Credentials for the storage system, either an access token
     *                    or Base-64 encoded "key:value", according to the configuration
     *                    Check parameter config.authType to determine which one is it.
     * @return true on success
     */
    boolean initService(StorageSystemConfig config, Vertx vertx, String storageElementUri, String storageAuth);

    /***
     * Get the human-readable name of the service.
     * @return Name of the transfer service.
     */
    String getServiceName();

    /***
     * Get the base URL of the service.
     * @return Base URL
     */
    public String getServiceBaseUrl();

    /**
     * List all files and sub-folders in a folder.
     * @param storageAuth Optional credentials for the destination storage, either access token or
     *                    Base-64 encoded "key:value"
     * @param folderUri The URI to the folder to list content of.
     * @return List of the folder content
     */
    Uni<StorageContent> listFolderContent(String storageAuth, String folderUri);

    /**
     * Get the details of a file or folder.
     * @param storageAuth Optional credentials for the destination storage, either access token or
     *                    Base-64 encoded "key:value"
     * @param seUri The URI to the file or folder to det details of.
     * @return Details about the storage element
     */
    Uni<StorageElement> getStorageElementInfo(String storageAuth, String seUri);

    /**
     * Create new folder.
     * @param storageAuth Optional credentials for the destination storage, either access token or
     *                    Base-64 encoded "key:value"
     * @param folderUri The URI to the folder to create.
     * @return Confirmation message
     */
    Uni<String> createFolder(String storageAuth, String folderUri);

    /**
     * Delete existing folder.
     * @param storageAuth Optional credentials for the destination storage, either access token or
     *                    Base-64 encoded "key:value"
     * @param folderUri The URI to the folder to create.
     * @return Confirmation message
     */
    Uni<String> deleteFolder(String storageAuth, String folderUri);

    /**
     * Delete existing file.
     * @param storageAuth Optional credentials for the destination storage, either access token or
     *                    Base-64 encoded "key:value"
     * @param fileUri The URI to the file to delete.
     * @return Confirmation message
     */
    Uni<String> deleteFile(String storageAuth, String fileUri);

    /**
     * Rename a folder or file.
     * @param storageAuth Optional credentials for the destination storage, either access token or
     *                    Base-64 encoded "key:value"
     * @param seOld The URI to the storage element to rename.
     * @param seNew The URI to the new name/location of the storage element.
     * @return Confirmation message
     */
    Uni<String> renameStorageElement(String storageAuth, String seOld, String seNew);
}
