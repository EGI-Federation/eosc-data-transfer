package parser.b2share.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Description in a B2Share record's metadata
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2ShareDescription {

    public String description;

    @JsonProperty("description_type")
    public String type;
}

