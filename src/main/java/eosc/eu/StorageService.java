package eosc.eu;

import io.smallrye.mutiny.Uni;

import eosc.eu.model.*;
import eosc.eu.TransferConfig.StorageSystemConfig;


/***
 * Generic data storage service abstraction
 */
public interface StorageService {

    /***
     * Initialize the service, avoids the need to inject configuration.
     * @param config Service configuration loaded from the config file
     * @param storageElementUrl the URL to a folder or file on the target storage system
     * @return true on success.
     */
    boolean initService(StorageSystemConfig config, String storageElementUrl);

    /***
     * Get the human-readable name of the service.
     * @return Name of the transfer service.
     */
    String getServiceName();

    /***
     * Get the base URL of the service.
     * @return Base URL.
     */
    public String getServiceBaseUrl();

    /**
     * Retrieve information about current user.
     * @param tsAuth The access token needed to call the service.
     * @return User information.
     */
    Uni<UserInfo> getUserInfo(String tsAuth);

    /**
     * List all files and sub-folders in a folder.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUrl The link to the folder to list content of.
     * @return List of the folder content.
     */
    Uni<StorageContent> listFolderContent(String tsAuth, String storageAuth, String folderUrl);

    /**
     * Get the details of a file or folder.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param seUrl The link to the file or folder to det details of.
     * @return Details about the storage element.
     */
    Uni<StorageElement> getStorageElementInfo(String tsAuth, String storageAuth, String seUrl);

    /**
     * Create new folder.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUrl The link to the folder to create.
     * @return Confirmation message.
     */
    Uni<String> createFolder(String tsAuth, String storageAuth, String folderUrl);

    /**
     * Delete existing folder.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUrl The link to the folder to delete.
     * @return Confirmation message.
     */
    Uni<String> deleteFolder(String tsAuth, String storageAuth, String folderUrl);

    /**
     * Delete existing file.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param fileUrl The link to the file to delete.
     * @return Confirmation message.
     */
    Uni<String> deleteFile(String tsAuth, String storageAuth, String fileUrl);

    /**
     * Rename a folder or file.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param seOld The link to the storage element to rename.
     * @param seNew The link to the new name/location of the storage element.
     * @return Confirmation message.
     */
    Uni<String> renameStorageElement(String tsAuth, String storageAuth, String seOld, String seNew);
}
