package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import parser.zenodo.model.ZenodoFile;
import parser.b2share.model.B2ShareFile;
import parser.esrf.model.EsrfDataFile;
import egi.fts.model.ObjectInfo;


/**
 * Details of a storage element (file or folder)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageElement extends StorageElementBase {

    public boolean isFolder = false;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public long size;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description="Date and time when element was created", example = "2022-10-15T20:14:22")
    public Date createdAt;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description="Date and time when element was last accessed", example = "2022-10-15T20:14:22")
    public Date accessedAt;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description="Date and time when element was last modified", example = "2022-10-15T20:14:22")
    public Date modifiedAt;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String mediaType;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public String accessUrl;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String downloadUrl;


    /**
     * Constructor
     */
    public StorageElement() { super("StorageElement"); }

    /**
     * Constructor
     */
    public StorageElement(String url, String type) {
        super("StorageElement");
        this.accessUrl = url;
        this.mediaType = type;
    }

    /**
     * Construct from Zenodo file
     */
    public StorageElement(ZenodoFile zf) {
        super("StorageElement", zf.filename);
        this.size = zf.filesize;
        this.mediaType = zf.getMediaType();
        this.accessUrl = zf.links.get("download");
        if(null != this.accessUrl && !this.accessUrl.isEmpty()) {
            this.downloadUrl = this.accessUrl + "?download=1";
        }
    }

    /**
     * Construct from ESRF file
     */
    public StorageElement(EsrfDataFile ef, String baseUrl) {
        super("StorageElement", ef.Datafile.name);
        this.size = ef.Datafile.fileSize;
        this.accessUrl = baseUrl + ef.Datafile.location;

        if(null != ef.Datafile.createTime)
            this.createdAt = ef.Datafile.createTime;

        if(null != ef.Datafile.modTime)
            this.modifiedAt = ef.Datafile.modTime;
    }

    /**
     * Construct from B2Share file
     */
    public StorageElement(B2ShareFile b2sf) {
        super("StorageElement", b2sf.name);
        this.size = b2sf.size;
        this.mediaType = b2sf.getMediaType();
        this.accessUrl = b2sf.links.get("self");

        if(null != b2sf.created)
            this.createdAt = b2sf.created;

        if(null != b2sf.modified)
            this.modifiedAt = b2sf.modified;
    }

    /**
     * Construct from FTS object
     */
    public StorageElement(ObjectInfo obj) {
        super("StorageElement");
        this.size = obj.size;
        this.accessUrl = obj.objectUrl;
        this.createdAt = obj.createdAt();
        this.accessedAt = obj.accessedAt();
        this.modifiedAt = obj.modifiedAt();
        this.isFolder = obj.isFolder();
        this.name = obj.getName();
    }
}
