package egi.eu;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import jakarta.annotation.PostConstruct;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.minio.*;
import io.minio.messages.Item;
import io.minio.messages.Bucket;

import eosc.eu.DataStorageCredentials;
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
    private String baseUri;
    private MinioAsyncClient minio;
    private int timeout;


    /***
     * Constructor
     */
    public MinioStorage() {}

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
            this.baseUri = uriStorageSystem.getScheme() + "://" + uriStorageSystem.getAuthority();
        } catch(URISyntaxException e) {
            log.error(e.getMessage());
            return false;
        }

        try {
            // Create the Minio client for the storage system
            var userInfo = new DataStorageCredentials(storageAuth);
            minio = MinioAsyncClient.builder()
                        .endpoint(this.baseUri)
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
    public String getServiceBaseUrl() { return this.baseUri; }

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
     * @return Tuple with (bucketName, objectName, baseUri), throws exceptions on error
     */
    private Uni<Tuple2<String, String>> getBucketObjectFromUri(String seUri) {
        // Split the storage element URI into a bucket and an object name
        java.lang.String bucketName = null;
        java.lang.String objectName = null;
        try {
            URI uri = new URI(seUri);
            if(!this.baseUri.equals(uri.getScheme() + "://" + uri.getAuthority())) {
                return Uni.createFrom().failure(new TransferServiceException("uriMismatch"));
            }

            var elements = uri.getPath().split("/");
            if(elements.length >= 1 && elements[0].isBlank())
                elements = Arrays.copyOfRange(elements, 1, elements.length);

            if(elements.length >= 1) {
                // If the URI includes a path
                bucketName = elements[0];
                elements = Arrays.copyOfRange(elements, 1, elements.length);

                objectName = elements.length > 0 ? StringUtils.join(elements, '/') : null;

                if(null != objectName && !objectName.isBlank() && '/' == seUri.charAt(seUri.length() - 1))
                    objectName += "/";
            }
        } catch(URISyntaxException e) {
            return Uni.createFrom().failure(new TransferServiceException(e, "uriInvalid"));
        }

        return Uni.createFrom().item(Tuple2.of(bucketName, objectName));
    }

    /**
     * List all buckets or all objects in a bucket.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUri The link to the bucket to list content of. If the URI has no path (or just /),
     *                  it will list all the buckets. If the path contains the bucket (with or without
     *                  a terminating /), it will return the objects and virtual folders directly in the
     *                  bucket. To list content of virtual folders, the folderUri must end in a slash (/).
     * @return List of folder content.
     */
    public Uni<StorageContent> listFolderContent(String auth, String storageAuth, String folderUri) {
        if(null == minio)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        var bucket = new AtomicReference<String>(null);
        var loa = new AtomicReference<ListObjectsArgs>(null);
        Uni<StorageContent> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("listFolderContentTimeout"))
            .chain(unused -> {
                // Split the storage element URI into bucket and object names
                return getBucketObjectFromUri(folderUri);
            })
            .chain(buckobj -> {
                // Got the URI parsed
                var bucketName = buckobj.getItem1();
                var objectName = buckobj.getItem2();
                if(null != objectName && '/' != objectName.charAt(objectName.length() - 1))
                    // This is an object, not a bucket, bail
                    return Uni.createFrom().failure(new TransferServiceException("notFolder"));

                if(null != bucketName && !bucketName.isBlank())
                    bucket.set(bucketName);
                else
                    // If no bucket name, then we need to list the buckets
                    return Uni.createFrom().nullItem();

                var listArgs = ListObjectsArgs.builder().bucket(bucketName);
                if(null != objectName && !objectName.isBlank())
                    listArgs.prefix(objectName);

                return Uni.createFrom().item(listArgs.build());
            })
            .chain(listArgs -> {
                if(null != listArgs) {
                    // If we have arguments for listing objects, skip this step
                    loa.set(listArgs);
                    return Uni.createFrom().nullItem();
                }

                CompletableFuture<List<Bucket>> buckets = null;
                try {
                    // List buckets
                    buckets = minio.listBuckets();

                } catch(Exception e) {
                    return Uni.createFrom().failure(new TransferServiceException(e, "listBuckets"));
                }

                return Uni.createFrom().completionStage(buckets);
            })
            .chain(bucketList -> {
                if(null != bucketList)
                    // If we got buckets, skip this step
                    return Uni.createFrom().item(Tuple2.of(bucketList, null));

                var listArgs = loa.get();
                Iterable<Result<Item>> objects = null;
                try {
                    // List objects
                    objects = minio.listObjects(listArgs);

                } catch(Exception e) {
                    return Uni.createFrom().failure(new TransferServiceException(e, "listObjects"));
                }
                return Uni.createFrom().item(Tuple2.of(null, objects));
            })
            .chain(contentLists -> {
                var buckets = (List<Bucket>)contentLists.getItem1();
                var objects = (Iterable<Result<Item>>)contentLists.getItem2();

                var content = new ArrayList<ObjectInfo>();
                String bucketUri = null;

                if(null != buckets) {
                    // Got list of buckets
                    bucketUri = this.baseUri;
                    for(var b : buckets) {
                        var objInfo = new ObjectInfo(null, b);
                        content.add(objInfo);
                    }
                }
                else if(null != objects) {
                    // Got list of objects
                    var bucketName = bucket.get();
                    bucketUri = this.baseUri + "/" + bucketName;
                    for(var object : objects)
                        try {
                            var item = object.get();
                            var objInfo = new ObjectInfo(bucketUri, item);
                            objInfo.bucket = bucketName;
                            content.add(objInfo);
                        }
                        catch(Exception e) {
                            return Uni.createFrom().failure(new TransferServiceException(e, "listObjects"));
                        }
                }

                MDC.put("seCount", content.size());
                return Uni.createFrom().item(new StorageContent(bucketUri, content));
            })
            .onFailure().invoke(e -> {
                log.error(e);
            });

        return result;
    }

    /**
     * Get the details of an object. Fails if called for a bucket.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param seUri The link to the file or folder to det details of.
     * @return Details about the object.
     */
    public Uni<StorageElement> getStorageElementInfo(String auth, String storageAuth, String seUri) {
        if(null == minio || null == this.baseUri)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<StorageElement> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("getStorageElementInfoTimeout"))
            .chain(unused -> {
                // Split the storage element URI into bucket and object names
                return getBucketObjectFromUri(seUri);
            })
            .chain(buckobj -> {
                // Got the URI parsed
                var bucketName = buckobj.getItem1();
                var objectName = buckobj.getItem2();
                if(null == objectName || objectName.isBlank() || '/' == objectName.charAt(objectName.length() - 1))
                    // This is not an object, bail
                    return Uni.createFrom().failure(new TransferServiceException("notFile"));

                // Build Minio param
                return Uni.createFrom().item(StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
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
     * Create new bucket.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUri The link to the bucket to create. The bucket name must be unique.
     * @return Confirmation message.
     */
    public Uni<String> createFolder(String auth, String storageAuth, String folderUri) {
        if(null == minio)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("createFolderTimeout"))
            .chain(unused -> {
                // Split the storage element URI into bucket and object names
                return getBucketObjectFromUri(folderUri);
            })
            .chain(buckobj -> {
                // Got the URI parsed
                var bucketName = buckobj.getItem1();
                var objectName = buckobj.getItem2();
                if(null != objectName && !objectName.isBlank() && '/' != objectName.charAt(objectName.length() - 1))
                    // This is an object, not a bucket, bail
                    return Uni.createFrom().failure(new TransferServiceException("notFolder"));

                if(null != objectName)
                    // For virtual folders we don't have to do anything
                    return Uni.createFrom().nullItem();

                // Build Minio param
                return Uni.createFrom().item(MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build());
            })
            .chain(makeArgs -> {
                // Check if we have to do anything
                if(null == makeArgs)
                    // Nope :-)
                    return Uni.createFrom().nullItem();

                // Ready to delete bucket
                CompletableFuture<Void> nope = null;
                try {
                    nope = minio.makeBucket(makeArgs);
                }
                catch(Exception e) {
                    return Uni.createFrom().failure(new TransferServiceException(e, "makeBucket"));
                }
                return Uni.createFrom().completionStage(nope);
            })
            .chain(unused -> {
                // If we got here, bucket was successfully deleted
                return Uni.createFrom().item("Success");
            })
            .onFailure().invoke(e -> {
                log.error(e);
            });

        return result;
    }

    /**
     * Delete existing bucket.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUrl The link to the bucket to delete.
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
     * Delete existing object.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param fileUri The link to the object to delete.
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
                // Split the storage element URI into bucket and object names
                return getBucketObjectFromUri(fileUri);
            })
            .chain(buckobj -> {
                // Got the URI parsed
                var bucketName = buckobj.getItem1();
                var objectName = buckobj.getItem2();
                if(null == objectName || (!objectName.isBlank() && '/' == objectName.charAt(objectName.length() - 1)))
                    // This is not an object, bail
                    return Uni.createFrom().failure(new TransferServiceException("notFile"));

                // Build Minio param
                return Uni.createFrom().item(RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
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
