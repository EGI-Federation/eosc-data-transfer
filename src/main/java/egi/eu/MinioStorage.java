package egi.eu;

import io.minio.errors.*;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.minio.*;
import io.minio.messages.Item;
import io.minio.messages.Bucket;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;

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
     * Initialize the client for the S3 storage system.
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
     * Extract the bucket and object name from an URI.
     * @param seUri is the fully qualified URI to the object
     * @return Uni containing Tuple with (bucketName, objectName), failure Uni on error.
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

                // Prepare to list objects
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
                    var msg = e.getMessage().replaceAll("\\.$", "");
                    return Uni.createFrom().failure(new TransferServiceException("listBuckets", msg));
                }

                return Uni.createFrom().completionStage(buckets);
            })
            .chain(bucketList -> {
                if(null != bucketList)
                    // If we got buckets, skip this step
                    return Uni.createFrom().item(Tuple2.of(bucketList, null));

                // List objects
                var listArgs = loa.get();
                var objects = minio.listObjects(listArgs);
                return Uni.createFrom().item(Tuple2.of(null, objects));
            })
            .chain(contentLists -> {
                // When we get here, we have either a list of buckets or a list of objects
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
                            var code = 0;
                            var type = e.getClass();
                            if(type.equals(ErrorResponseException.class))
                                code = ((ErrorResponseException)e).response().code();
                            else if(type.equals(ServerException.class))
                                code = ((ServerException)e).statusCode();
                            return Uni.createFrom().failure(new TransferServiceException("listObjects", code,
                                                                        e.getMessage().replaceAll("\\.$", "")));
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

                // Prepare to get object statistics
                return Uni.createFrom().item(StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build());
            })
            .chain(statArgs -> {
                CompletableFuture<StatObjectResponse> stats = null;
                try {
                    // Get storage element stats
                    stats = minio.statObject(statArgs);
                }
                catch(Exception e) {
                    var msg = e.getMessage().replaceAll("\\.$", "");
                    return Uni.createFrom().failure(new TransferServiceException("statObject", msg));
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

                // Prepare to make bucket
                return Uni.createFrom().item(MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build());
            })
            .chain(makeArgs -> {
                if(null == makeArgs)
                    return Uni.createFrom().nullItem();

                CompletableFuture<Void> nope = null;
                try {
                    // Create bucket
                    nope = minio.makeBucket(makeArgs);
                }
                catch(Exception e) {
                    var msg = e.getMessage().replaceAll("\\.$", "");
                    return Uni.createFrom().failure(new TransferServiceException("makeBucket", msg));
                }
                return Uni.createFrom().completionStage(nope);
            })
            .chain(unused -> {
                // If we got here, bucket was successfully created
                return Uni.createFrom().item("Created");
            })
            .onFailure().invoke(e -> {
                log.error(e);
            });

        return result;
    }

    /**
     * Delete existing bucket or virtual folder. If deleting a bucket, the bucket must be empty.
     * If deleting a virtual folder, it will delete all objects in that virtual folder (and deeper).
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUri The link to the bucket or virtual folder to delete.
     * @return Confirmation message.
     */
    public Uni<String> deleteFolder(String auth, String storageAuth, String folderUri) {
        if(null == minio)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        var bucket = new AtomicReference<String>(null);
        var message = new AtomicReference<String>("Deleted");
        var objectCount = new AtomicReference<Integer>(null);
        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
            .after(Duration.ofMillis(this.timeout))
            .failWith(new TransferServiceException("deleteFolderTimeout"))
            .chain(unused -> {
                // Split the storage element URI into bucket and object names
                return getBucketObjectFromUri(folderUri);
            })
            .chain(buckobj -> {
                // Got the URI parsed
                var bucketName = buckobj.getItem1();
                var objectName = buckobj.getItem2();
                if(null != objectName && !objectName.isBlank() && '/' != objectName.charAt(objectName.length() - 1))
                    // This is an object, not a virtual folder, bail
                    return Uni.createFrom().failure(new TransferServiceException("notFolder"));

                bucket.set(bucketName);

                if(null == objectName)
                    // Deleting a bucket, skip this step
                    return Uni.createFrom().nullItem();

                // For virtual folders prepare to list objects
                return Uni.createFrom().item(ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(objectName)
                        .build());
            })
            .chain(listArgs -> {
                if(null != listArgs) {
                    // If we have arguments for listing objects, use it
                    Iterable<Result<Item>> objects = minio.listObjects(listArgs);
                    return Uni.createFrom().item(objects);
                }

                // Deleting a bucket, skip this step
                return Uni.createFrom().nullItem();
            })
            .chain(objectsToDelete -> {
                if(null != objectsToDelete) {
                    // If we have a list of objects, prepare to delete them
                    List<DeleteObject> objects = new LinkedList<>();
                    var bucketName = bucket.get();
                    var bucketUri = this.baseUri + "/" + bucketName;

                    for(var object : objectsToDelete)
                        try {
                            var item = object.get();
                            var objInfo = new ObjectInfo(bucketUri, item);
                            objects.add(new DeleteObject(objInfo.getName()));
                        }
                        catch(Exception e) {
                            var code = 0;
                            var type = e.getClass();
                            if(type.equals(ErrorResponseException.class))
                                code = ((ErrorResponseException)e).response().code();
                            else if(type.equals(ServerException.class))
                                code = ((ServerException)e).statusCode();
                            return Uni.createFrom().failure(new TransferServiceException("deleteObject", code,
                                                                                e.getMessage().replaceAll("\\.$", "")));
                        }

                    MDC.put("objectCount", objects.size());
                    objectCount.set(objects.size());

                    // Prepare to delete objects
                    return Uni.createFrom().item(RemoveObjectsArgs.builder()
                            .bucket(bucketName)
                            .objects(objects)
                            .build());
                }

                // Deleting a bucket, skip this step
                return Uni.createFrom().nullItem();
            })
            .chain(removeArgs -> {
                if(null != removeArgs) {
                    // If we have arguments for deleting objects, use it
                    Iterable<Result<DeleteError>> deleted = minio.removeObjects(removeArgs);
                    return Uni.createFrom().item(deleted);
                }

                // Deleting a bucket, skip this step
                return Uni.createFrom().nullItem();
            })
            .chain(deleteErrors -> {
                if(null != deleteErrors) {
                    // If we got object delete errors, package them for the response
                    List<Tuple2<String, String>> details = new ArrayList<>();
                    for(var error : deleteErrors) {
                        try {
                            var e = error.get();
                            var code = e.code();
                            var msg = e.message();
                            details.add(Tuple2.of(code, msg));
                        } catch(Exception e) {
                            var code = 0;
                            var type = e.getClass();
                            if(type.equals(ErrorResponseException.class))
                                code = ((ErrorResponseException)e).response().code();
                            else if(type.equals(ServerException.class))
                                code = ((ServerException)e).statusCode();
                            return Uni.createFrom().failure(new TransferServiceException("deleteError", code,
                                                                                    e.getMessage().replaceAll("\\.$", "")));
                        }
                    }

                    if(details.isEmpty()) {
                        // Success
                        var msg = String.format("Deleted %d prefixed objects", objectCount.get());
                        log.info(msg);
                        message.set(msg);
                    }
                    else {
                        MDC.put("errorCount", details.size());
                        log.infof("Failed to delete %d prefixed objects", objectCount.get());
                        return Uni.createFrom().failure(new TransferServiceException("deleteError", details));
                    }

                    return Uni.createFrom().nullItem();
                }

                // Prepare to delete a bucket
                var bucketName = bucket.get();
                return Uni.createFrom().item(RemoveBucketArgs.builder()
                        .bucket(bucketName)
                        .build());
            })
            .chain(bucketArgs -> {
                if(null != bucketArgs) {
                    // If we have arguments for deleting a bucket, use it
                    CompletableFuture<Void> deleted = null;
                    try {
                        // Delete bucket
                        deleted = minio.removeBucket(bucketArgs);

                    } catch(Exception e) {
                        var msg = e.getMessage().replaceAll("\\.$", "");
                        return Uni.createFrom().failure(new TransferServiceException("removeBucket", msg));
                    }
                    return Uni.createFrom().completionStage(deleted);
                }

                // Deleting virtual folder, nothing else left to do
                return Uni.createFrom().nullItem();
            })
            .chain(unused -> {
                // If we got here, bucket or prefixed objects were deleted
                return Uni.createFrom().item(message.get());
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

                // Prepare to delete object
                return Uni.createFrom().item(RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build());
            })
            .chain(removeArgs -> {
                CompletableFuture<Void> nope = null;
                try {
                    // Delete object
                    nope = minio.removeObject(removeArgs);
                }
                catch(Exception e) {
                    var msg = e.getMessage().replaceAll("\\.$", "");
                    return Uni.createFrom().failure(new TransferServiceException("removeObject", msg));
                }
                return Uni.createFrom().completionStage(nope);
            })
            .chain(unused -> {
                // If we got here, object was successfully deleted
                return Uni.createFrom().item("Deleted");
            })
            .onFailure().invoke(e -> {
                log.error(e);
            });

        return result;
    }

    /**
     * Rename an object. Attempts to rename a bucket or a virtual folder will fail.
     * Note: Rename is not supported even for objects. Instead, the object is copied to
     *       the new location with the new name, then the old one will be deleted.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param seOld The link to the storage element to rename.
     * @param seNew The link to the new name/location of the storage element.
     * @return Confirmation message.
     */
    public Uni<String> renameStorageElement(String auth, String storageAuth, String seOld, String seNew) {
        if(null == minio)
            throw new TransferServiceException("configInvalid");

        var bucketOld = new AtomicReference<String>(null);
        var objectOld = new AtomicReference<String>(null);
        var bucketNew = new AtomicReference<String>(null);
        var objectNew = new AtomicReference<String>(null);
        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
            .after(Duration.ofMillis(this.timeout))
            .failWith(new TransferServiceException("renameStorageElementTimeout"))
            .chain(unused -> {
                // Split the new storage element URI into bucket and object names
                return getBucketObjectFromUri(seNew);
            })
            .chain(buckobj -> {
                // Got the new URI parsed
                var bucketName = buckobj.getItem1();
                var objectName = buckobj.getItem2();

                if(null == objectName || objectName.isBlank() ||
                    '/' == objectName.charAt(objectName.length() - 1))
                    // This is a bucket or a virtual folder, bail
                    return Uni.createFrom().failure(new TransferServiceException("notFile"));

                bucketNew.set(bucketName);
                objectNew.set(objectName);

                // Split the old storage element URI into bucket and object names
                return getBucketObjectFromUri(seOld);
            })
            .chain(buckobj -> {
                // Got the old URI parsed
                var bucketName = buckobj.getItem1();
                var objectName = buckobj.getItem2();

                if(null == objectName || objectName.isBlank() ||
                    '/' == objectName.charAt(objectName.length() - 1))
                    // The old location cannot be a bucket or a virtual folder either, bail
                    return Uni.createFrom().failure(new TransferServiceException("notFile"));

                bucketOld.set(bucketName);
                objectOld.set(objectName);

                var bucketNameNew = bucketNew.get();
                var objectNameNew = objectNew.get();

                if(bucketName.equals(bucketNameNew) && objectName.equals(objectNameNew))
                    // Source and destination the same, nothing to do
                    return Uni.createFrom().failure(new TransferServiceException("noOp",
                                                                    "Source and destination the same, nothing to do"));

                // Prepare to copy object
                return Uni.createFrom().item(CopyObjectArgs.builder()
                                .bucket(bucketNameNew)
                                .object(objectNameNew)
                                .source(CopySource.builder()
                                    .bucket(bucketName)
                                    .object(objectName)
                                    .build())
                                .build());
            })
            .chain(copyArgs -> {
                CompletableFuture<ObjectWriteResponse> copied = null;
                try {
                    // Copy object to new location
                    copied = minio.copyObject(copyArgs);
                } catch(Exception e) {
                    var msg = e.getMessage().replaceAll("\\.$", "");
                    return Uni.createFrom().failure(new TransferServiceException("copyObject", msg));
                }
                return Uni.createFrom().completionStage(copied);
            })
            .chain(copied -> {
                // Object copied to new location
                if(null == copied)
                    return Uni.createFrom().failure(new TransferServiceException("copyObject"));

                var bucketName = bucketOld.get();
                var objectName = objectOld.get();

                // Prepare to delete original object
                return Uni.createFrom().item(RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build());
            })
            .chain(removeArgs -> {
                CompletableFuture<Void> removed = null;
                try {
                    // Delete object from old location
                    removed = minio.removeObject(removeArgs);
                } catch(Exception e) {
                    var msg = e.getMessage().replaceAll("\\.$", "");
                    return Uni.createFrom().failure(new TransferServiceException("removeObject", msg));
                }
                return Uni.createFrom().completionStage(removed);
            })
            .chain(unused -> {
                // If we got here, object was moved
                return Uni.createFrom().item("Renamed");
            })
            .onFailure().invoke(e -> {
                log.error(e);
            });

        return result;
    }
}
