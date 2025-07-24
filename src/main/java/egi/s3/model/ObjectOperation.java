package egi.fts.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Details of a file or folder operation
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ObjectOperation {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("surl")
    public String seUrl;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("old")
    public String seUrlOld;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("new")
    public String seUrlNew;


    /**
     * Constructor
     */
    public ObjectOperation() {}

    /**
     * Construct from object URL
     */
    public ObjectOperation(String surl) { this.seUrl = surl; }

    /**
     * Construct from old and new object URLs
     */
    public ObjectOperation(String surlOld, String surlNew) {
        this.seUrlOld = surlOld;
        this.seUrlNew = surlNew;
    }
}
