package parser.b2share.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Contributor in a B2Share record's metadata
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2ShareContributor {

    @JsonProperty("contributor_name")
    public String name;

    @JsonProperty("contributor_type")
    public String type;

    public B2ShareContributor() {}
}
