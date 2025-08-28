package egi.s3.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.minio.StatObjectResponse;
import io.minio.messages.RetentionMode;
import org.jboss.logging.Logger;

import java.time.ZonedDateTime;
import java.util.Date;


/**
 * Details of a file or folder from a storage
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ObjectInfo {

    private static final Logger LOG = Logger.getLogger(ObjectInfo.class);

    public String seUri; // Aka storage URI (seUri)
    public String etag;
    public String object;
    public String bucket;
    public ZonedDateTime ctime;
    public ZonedDateTime atime;
    public ZonedDateTime mtime;
    public long size;
    public String mediaType;
    public boolean deleteMarker;
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
        this.mtime = so.lastModified();
        this.size = so.size();
        this.mediaType = so.headers().get("Content-Type");
        this.deleteMarker = so.deleteMarker();
        this.retentionMode = so.retentionMode();
    }

    /***
     * Extract filename from URL
     * @return The name of the object
     */
    public String getName() {
        if(null != this.object && !this.object.isEmpty())
            return this.object;

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
