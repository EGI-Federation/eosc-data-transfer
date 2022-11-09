package parser.b2share.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Item in a B2Share record
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2ShareItem {

    @JsonProperty("version_id")
    public String versionId;

    @JsonProperty("ePIC_PID")
    public String ePIC;
    public String bucket;
    public String checksum;
    public String key;
    public long size;

    public B2ShareItem() {}
}
