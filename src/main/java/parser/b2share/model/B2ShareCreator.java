package parser.b2share.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Creator in a B2Share record's metadata
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2ShareCreator {

    @JsonProperty("creator_name")
    public String name;

    public B2ShareCreator() {}
}
