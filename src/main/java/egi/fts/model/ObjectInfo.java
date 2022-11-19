package egi.fts.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jboss.logging.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Details of a file or folder from a storage
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ObjectInfo {

    private static final Logger LOG = Logger.getLogger(ObjectInfo.class);

    static int S_IFDIR = 0x4000; // Directory
    static int S_IFREG = 0x8000; // Regular file

    @JsonIgnore
    private URL url;

    public String name;
    public String objectUrl; // Aka storage URL (surl)
    public long ctime;
    public long atime;
    public long mtime;
    public long size;
    public int mode;
    public int nlink;


    /**
     * Constructor
     */
    public ObjectInfo() {}

    /***
     * Extract filename from URL
     * @return The name of the object
     */
    public String getName() {
        if(null != this.name && !this.name.isEmpty() && !objectUrl.isBlank())
            return this.name;

        if(null == this.objectUrl || this.objectUrl.isEmpty() || this.objectUrl.isBlank())
            return null;

        if(null == this.url) {
            try {
                this.url = new URL(this.objectUrl);
            } catch (MalformedURLException e) {
                LOG.error(e.getMessage());
                return null;
            }
        }

        // String trailing separator
        String path = this.url.getFile();
        Pattern p = Pattern.compile("^(.+)/$");
        Matcher m = p.matcher(path);
        if(m.matches())
            path = m.group(1);

        // Get last path segment
        p = Pattern.compile("^(.*)/(.+)$");
        m = p.matcher(path);
        if(m.matches()) {
            this.name = m.group(2);
            return this.name;
        }

        return null;
    }

    /***
     * Check file type from Linux file mode
     * @return true if a folder (directory)
     */
    public boolean isFolder() {
        return (mode & S_IFDIR) > 0;
    }

    /***
     * Convert creation Linux file time to Java Date
     * @return Date and time of creation
     */
    public Date createdAt() {
        return new Date((long)ctime*1000);
    }

    /***
     * Convert last accessed Linux file time to Java Date
     * @return Date and time of last access
     */
    public Date accessedAt() {
        return new Date((long)atime*1000);
    }

    /***
     * Convert last change Linux file time to Java Date
     * @return Date and time of last change
     */
    public Date modifiedAt() {
        return new Date((long)mtime*1000);
    }
}
