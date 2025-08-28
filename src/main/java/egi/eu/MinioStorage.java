package egi.eu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eosc.eu.DataStorageCredentials;
import io.minio.*;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import jakarta.annotation.PostConstruct;

import eosc.eu.StorageService;
import eosc.eu.TransferServiceException;
import eosc.eu.TransferConfig.StorageSystemConfig;
import eosc.eu.model.*;

import egi.s3.model.*;


/***
 * Class for manipulating storage elements in S3 storage systems
 */
public class MinioStorage implements StorageService {

    private static final Logger log = Logger.getLogger(MinioStorage.class);

    private String name;
    private String baseUrl;
    private MinioAsyncClient minio;
    private int timeout;

    private ObjectMapper objectMapper;


    /***
     * Constructor
     */
    public MinioStorage() { this.objectMapper = new ObjectMapper(); }

    /***
     * Initialize the client for the S3 storage system
     * @param serviceConfig Configuration loaded from the config file
     * @param storageElementUrl the URL to a folder or file on the target storage system
     * @param storageAuth Credentials for the storage system, Base-64 encoded "accesskey:secretkey"
     * @return true on success
     */
    @PostConstruct
    public boolean initService(StorageSystemConfig serviceConfig, String storageElementUrl, String storageAuth) {

        if(null != minio)
            return true;

        this.name = serviceConfig.name();
        this.timeout = serviceConfig.timeout();

        MDC.put("storageElement", storageElementUrl);
        log.debug("Obtaining client for S3 compatible object storage");

        // Get the base URL for the S3 storage system
        try {
            URI uriStorageSystem = new URI(storageElementUrl);
            this.baseUrl = uriStorageSystem.getScheme() + "://" + uriStorageSystem.getAuthority();
        } catch(URISyntaxException e) {
            log.error(e.getMessage());
            return false;
        }

        try {
            // Create the Minio client for the storage system
            var userInfo = new DataStorageCredentials(storageAuth);
            minio = MinioAsyncClient.builder()
                        .endpoint(this.baseUrl)
                        .credentials(userInfo.getAccessKey(), userInfo.getSecretKey())
                        .build();

            return true;
        }
        catch(IllegalArgumentException iae) {
            log.error(iae.getMessage());
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
     * @return Base URL.
     */
    public String getServiceBaseUrl() { return this.baseUrl; }

    /**
     * Retrieve information about current user.
     * @param auth The access token that authorizes calling the service.
     * @return User information.
     */
    public Uni<eosc.eu.model.UserInfo> getUserInfo(String auth) {
        if(null == minio)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        return Uni.createFrom().failure(new TransferServiceException("configInvalid"));
    }

    /***
     * Extract the bucket and object name from an URI that points to an object in object storage
     * @param seUri is the fully qualified URI to the object
     * @return Tuple with bucketName, objectName, throws exceptions on error
     */
    private Uni<Tuple2<String, String>> getBucketObjectFromUri(String seUri) {
        // Split the storage element URI into a bucket and an object name
        java.lang.String bucketName = null;
        java.lang.String objectName = null;
        try {
            URI uri = new URI(seUri);
            if(!this.baseUrl.equals(uri.getScheme() + "://" + uri.getAuthority())) {
                return Uni.createFrom().failure(new TransferServiceException("uriMismatch"));
            }

            var elements = uri.getPath().split("/");
            if(elements.length >= 1 && elements[0].isBlank())
                elements = Arrays.copyOfRange(elements, 1, elements.length);

            if(elements.length < 1)
                // No bucket or object in URI
                return Uni.createFrom().failure(new TransferServiceException("seInvalid"));

            bucketName = elements[0];
            elements = Arrays.copyOfRange(elements, 1, elements.length);

            objectName = StringUtils.join(elements, '/');

        } catch(URISyntaxException e) {
            return Uni.createFrom().failure(new TransferServiceException(e, "uriInvalid"));
        }

        return Uni.createFrom().item(Tuple2.of(bucketName, objectName));
    }

    /**
     * List all files and sub-folders in a folder.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUrl The link to the folder to list content of.
     * @return List of folder content.
     */
    public Uni<StorageContent> listFolderContent(String auth, String storageAuth, String folderUrl) {
        if(null == minio)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<StorageContent> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("listFolderContentTimeout"))
//            .chain(unused -> {
//                // When necessary, configure S3 destination
//                if(null != storageAuth && !storageAuth.isBlank())
//                    return prepareStorages(auth, storageAuth, List.of(folderUrl));
//
//                return Uni.createFrom().item(true);
//            })
            .chain(s3ConfigResult -> {
                // List folder content
                //return minio.listFolderContentAsync(auth, folderUrl);
                return Uni.createFrom().failure(new TransferServiceException("configInvalid"));
            })
            .chain(contentList -> {
                // Got folder listing
                //MDC.put("seCount", contentList.size());
                return Uni.createFrom().item(new StorageContent(folderUrl, null));
            })
            .onFailure().invoke(e -> {
                log.error(e);
            });

        return result;
    }

    /**
     * Get the details of a file or folder.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param seUri The link to the file or folder to det details of.
     * @return Details about the storage element.
     */
    public Uni<StorageElement> getStorageElementInfo(String auth, String storageAuth, String seUri) {
        if(null == minio || null == this.baseUrl)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<StorageElement> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("getStorageElementInfoTimeout"))
            .chain(unused -> {
                // Split the storage element URI into a bucket and an object name
                return getBucketObjectFromUri(seUri);
            })
            .chain(buckobj -> {
                // Build Minio param
                return Uni.createFrom().item(StatObjectArgs.builder()
                        .bucket(buckobj.getItem1())
                        .object(buckobj.getItem2())
                        .build());
            })
            .chain(statArgs -> {
                // Ready to get storage element stats
                CompletableFuture<StatObjectResponse> stats = null;
                try {
                    stats = minio.statObject(statArgs);
                }
                catch(Exception e) {
                    return Uni.createFrom().failure(new TransferServiceException(e, "statObject"));
                }
                return Uni.createFrom().completionStage(stats);
            })
            .chain(stats -> {
                // Got storage element stats
                var objInfo = new ObjectInfo(seUri, stats);
                return Uni.createFrom().item(new StorageElement(objInfo));
            })
            .onFailure().invoke(e -> {
                log.error(e);
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
        if(null == minio)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("createFolderTimeout"))
//            .chain(unused -> {
//                // When necessary, configure S3 destination
//                if(null != storageAuth && !storageAuth.isBlank())
//                    return prepareStorages(auth, storageAuth, List.of(folderUrl));
//
//                return Uni.createFrom().item(true);
//            })
            .chain(s3ConfigResult -> {
                // Create folder
                var operation = new ObjectOperation(folderUrl);
                //return minio.createFolderAsync(auth, operation);
                return Uni.createFrom().item("fsh");
            })
            .chain(code -> {
                // Got success code
                return Uni.createFrom().item(code);
            })
            .onFailure().invoke(e -> {
                log.error(e);
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
        if(null == minio)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("deleteFolderTimeout"))
//            .chain(unused -> {
//                // When necessary, configure S3 destination
//                if(null != storageAuth && !storageAuth.isBlank())
//                    return prepareStorages(auth, storageAuth, List.of(folderUrl));
//
//                return Uni.createFrom().item(true);
//            })
            .chain(s3ConfigResult -> {
                // Delete folder
                var operation = new ObjectOperation(folderUrl);
                //return minio.deleteFolderAsync(auth, operation);
                return Uni.createFrom().item("fsh");
            })
            .chain(code -> {
                // Got success code
                return Uni.createFrom().item(code);
            })
            .onFailure().invoke(e -> {
                log.error(e);
            });

        return result;
    }

    /**
     * Delete existing file.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param fileUri The link to the file to delete.
     * @return Confirmation message.
     */
    public Uni<String> deleteFile(String auth, String storageAuth, String fileUri) {
        if(null == minio)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("deleteFileTimeout"))
            .chain(unused -> {
                // Split the storage element URI into a bucket and an object name
                return getBucketObjectFromUri(fileUri);
            })
            .chain(buckobj -> {
                // Build Minio param
                return Uni.createFrom().item(RemoveObjectArgs.builder()
                        .bucket(buckobj.getItem1())
                        .object(buckobj.getItem2())
                        .build());
            })
            .chain(removeArgs -> {
                // Ready to delete file
                CompletableFuture<Void> nope = null;
                try {
                    nope = minio.removeObject(removeArgs);
                }
                catch(Exception e) {
                    return Uni.createFrom().failure(new TransferServiceException(e, "removeObject"));
                }
                return Uni.createFrom().completionStage(nope);
            })
            .chain(unused -> {
                // If we got here, object was successfully deleted
                return Uni.createFrom().item("Success");
            })
            .onFailure().invoke(e -> {
                log.error(e);
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
        if(null == minio)
            throw new TransferServiceException("configInvalid");

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("renameStorageElementTimeout"))
//            .chain(unused -> {
//                // When necessary, configure S3 destination
//                if(null != storageAuth && !storageAuth.isBlank())
//                    return prepareStorages(auth, storageAuth, List.of(seOld));
//
//                return Uni.createFrom().item(true);
//            })
            .chain(s3ConfigResult -> {
                // Rename storage element
                var operation = new ObjectOperation(seOld, seNew);
                //return minio.renameObjectAsync(auth, operation);
                return Uni.createFrom().item("fsh");
            })
            .chain(code -> {
                // Got success code
                return Uni.createFrom().item(code);
            })
            .onFailure().invoke(e -> {
                log.error(e);
            });

        return result;
    }
}
