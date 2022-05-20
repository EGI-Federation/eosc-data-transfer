package eosc.eu.model;

import parser.zenodo.model.ZenodoFile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import javax.ws.rs.core.MediaType;


/**
 * Details of a storage element file or folder)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageElement extends StorageElementBase {

    public String kind = "StorageElement";
    public boolean isFolder = false;
    public long size;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Date createdAt = null;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Date accessedAt = null;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Date modifiedAt = null;

    public String mediaType;
    public String accessUrl;
    public String downloadUrl;

    public StorageElement() {}

    public StorageElement(ZenodoFile zf) {
        super("StorageElement");
        this.name = zf.filename;
        this.size = zf.filesize;
        this.mediaType = zf.getMediaType();
        this.accessUrl = zf.links.get("download");
        if(null != this.accessUrl && !this.accessUrl.isEmpty()) {
            this.downloadUrl = this.accessUrl + "?download=1";
        }
    }
}
