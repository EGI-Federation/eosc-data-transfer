package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import parser.zenodo.model.ZenodoFile;
import parser.b2share.model.B2ShareFile;
import parser.esrf.model.EsrfDataFile;
import egi.s3.model.ObjectInfo;


/**
 * Details of a storage element (file or folder)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "A file or folder in a storage system or data repository")
public class StorageElement extends StorageElementBase {

    @Schema(description = "True for folders, containers and buckets", defaultValue = "false")
    public boolean isFolder = false;

    @Schema(description = "False when the element exists but the caller cannot read it", defaultValue = "true")
    public boolean isAccessible = true;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @Schema(description = "Size of the file in bytes")
    public long size;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    @Schema(implementation = String.class, format = "date-time",
            description="Date and time when element was created", example = "2022-10-15T20:14:22+0100")
    public Date createdAt;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    @Schema(implementation = String.class, format = "date-time",
            description="Date and time when element was last accessed",
            example = "2022-10-15T20:14:22+0100")
    public Date accessedAt;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    @Schema(implementation = String.class, format = "date-time",
            description="Date and time when element was last modified",
            example = "2022-10-15T20:14:22+0100")
    public Date modifiedAt;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Media type of the file", example = "application/zip")
    public String mediaType;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "URI that can be used as a source in a transfer",
            example = "https://zenodo.org/record/6511035/files/data.zip")
    public String accessUrl;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "URI that streams the content of the file",
            example = "https://zenodo.org/record/6511035/files/data.zip?download=1")
    public String downloadUrl;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="File checksum in the form `algorithm:value`")
    public String checksum;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="Can be used to group files in file sets (collections). This only " +
                        "allows one level of grouping, for a deeper hierarchical structure " +
                        "use the field 'path'.")
    public String collection;


    /**
     * Constructor
     */
    public StorageElement() { super("StorageElement"); }

    /**
     * Construct using access URL and media type
     * @param url Access URL for the storage element
     * @param type Media type
     */
    public StorageElement(String url, String type) {
        super("StorageElement");
        this.accessUrl = url;
        this.mediaType = type;
    }

    /**
     * Construct from Zenodo file
     * @param zf Zenodo file
     */
    public StorageElement(ZenodoFile zf) {
        super("StorageElement", zf.key);
        this.size = zf.size;
        this.checksum = zf.checksum;
        this.mediaType = zf.getMediaType();
        this.accessUrl = zf.links.get("download");
        if(null == this.accessUrl)
            this.accessUrl = zf.links.get("self");
        if(null != this.accessUrl && !this.accessUrl.isEmpty()) {
            this.downloadUrl = this.accessUrl + "?download=1";
        }
    }

    /**
     * Construct from ESRF file
     * @param ef ESRF file
     * @param baseUrl The base URL for the access URL, as the ESRF file
     *                only contains the path to the file
     * @param sessionId The ESRF session
     */
    public StorageElement(EsrfDataFile ef, String baseUrl, String sessionId) {
        super("StorageElement", ef.Datafile.name);
        this.size = ef.Datafile.fileSize;
        this.accessUrl = ef.accessUrl(baseUrl, sessionId);
        if(null != ef.Datafile.createTime)
            this.createdAt = ef.Datafile.createTime;
        if(null != ef.Datafile.modTime)
            this.modifiedAt = ef.Datafile.modTime;
        if(null != ef.Datafile.dataset && null != ef.Datafile.dataset.name)
            this.collection = ef.Datafile.dataset.name;
        if(null != this.accessUrl && !this.accessUrl.isEmpty())
            this.downloadUrl = this.accessUrl + "&download=1";
    }

    /**
     * Construct from B2Share file
     * @param b2sf B2SHARE file
     */
    public StorageElement(B2ShareFile b2sf) {
        super("StorageElement", b2sf.name);
        this.size = b2sf.size;
        this.checksum = b2sf.checksum;
        this.mediaType = b2sf.getMediaType();
        this.accessUrl = b2sf.links.get("self");

        if(null != b2sf.created)
            this.createdAt = b2sf.created;

        if(null != b2sf.modified)
            this.modifiedAt = b2sf.modified;
    }

    /**
     * Construct from Minio object
     * @param obj Information about an object returned by Minio
     */
    public StorageElement(ObjectInfo obj) {
        super("StorageElement");
        this.size = obj.size;
        this.accessUrl = obj.seUri;
        this.createdAt = obj.createdAt();
        this.accessedAt = obj.accessedAt();
        this.modifiedAt = obj.modifiedAt();
        this.isFolder = obj.isFolder;
        this.name = obj.getName();

        if(!isFolder && null != obj.bucket && !obj.bucket.isBlank())
            this.path = obj.bucket;
    }
}
