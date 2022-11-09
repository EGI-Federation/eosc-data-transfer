package parser.b2share.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;


/**
 * Content of a B2Share bucket
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2ShareBucket {

    public String id;
    public boolean locked;
    public long size;
    public Date created;

    @JsonProperty("updated")
    public Date modified;

    @JsonProperty("max_file_size")
    public long maxFileSize;

    @JsonProperty("quota_size")
    public long quota;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<B2ShareFile> contents;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> links;

    public B2ShareBucket() {
        this.contents = new ArrayList<>();
        this.links = new HashMap<>();
    }

}
