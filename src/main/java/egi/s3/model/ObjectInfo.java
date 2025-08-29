package egi.s3.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jboss.logging.Logger;

import java.time.ZonedDateTime;
import java.util.Date;

import io.minio.StatObjectResponse;
import io.minio.messages.RetentionMode;
import io.minio.messages.Item;
import io.minio.messages.Bucket;


/**
 * Details of a file or folder from a storage
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ObjectInfo {

    private static final Logger LOG = Logger.getLogger(ObjectInfo.class);

    public String seUri; // Aka storage URI (seUri)
    public String etag;
    public String bucket;
    public String object;
    public boolean isFolder; // Is a virtual folder
    public ZonedDateTime ctime;
    public ZonedDateTime atime;
    public ZonedDateTime mtime;
    public long size;
    public String mediaType;
    public boolean deleteMarker;
    public boolean isLatest;
    public String versionId;
    public String storageClass;
    public RetentionMode retentionMode;

    /**
     * Constructor
     */
    public ObjectInfo() {}

    /**
     * Construct from Minio's StatObjectResponse
     */
    public ObjectInfo(String seUri, StatObjectResponse so) {
        this.seUri = seUri;
        this.etag = so.etag();
        this.object = so.object();
        this.bucket = so.bucket();
        this.isFolder = false;
        this.mtime = so.lastModified();
        this.size = so.size();
        this.mediaType = so.headers().get("Content-Type");
        this.versionId = so.versionId();
        this.deleteMarker = so.deleteMarker();
        this.retentionMode = so.retentionMode();
    }

    /**
     * Construct from Minio's Item
     */
    public ObjectInfo(String seUri, Item item) {
        this.seUri = seUri;
        this.etag = item.etag();
        this.object = item.objectName();
        this.isFolder = item.isDir();
        this.mtime = item.lastModified();
        this.size = item.size();
        this.isLatest = item.isLatest();
        this.versionId = item.versionId();
        this.deleteMarker = item.isDeleteMarker();
        this.storageClass = item.storageClass();
    }

    /**
     * Construct from Minio's Item
     */
    public ObjectInfo(String seUri, Bucket bucket) {
        this.seUri = seUri;
        this.bucket = bucket.name();
        this.isFolder = true;
        this.ctime = bucket.creationDate();
    }

    /***
     * Extract filename from URL
     * @return The name of the object
     */
    public String getName() {
        if(null != this.object && !this.object.isEmpty())
            return this.object;

        if(null != this.bucket && !this.bucket.isEmpty())
            return this.bucket + "/";

        return null;
    }

    /***
     * Check file type, if we have an object name then it's an object
     * @return true if a folder (directory)
     */
    public boolean isFolder() {
        return null == object || object.isBlank();
    }

    /***
     * Convert zoned creation time to Java Date
     * @return Date and time of creation
     */
    public Date createdAt() { return null != ctime ? Date.from(ctime.toInstant()) : null; }

    /***
     * Convert zoned last accessed time to Java Date
     * @return Date and time of last access
     */
    public Date accessedAt() { return null != atime ? Date.from(atime.toInstant()) : null; }

    /***
     * Convert zoned last change time to Java Date
     * @return Date and time of last change
     */
    public Date modifiedAt() { return null != mtime ? Date.from(mtime.toInstant()) : null; }
}
