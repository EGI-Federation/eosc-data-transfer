package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import egi.fts.model.ObjectInfo;
import parser.zenodo.model.ZenodoFile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import javax.ws.rs.core.MediaType;


/**
 * Details of a storage element (file or folder)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageElement extends StorageElementBase {

    public boolean isFolder = false;
    public long size;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date createdAt;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date accessedAt;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
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
