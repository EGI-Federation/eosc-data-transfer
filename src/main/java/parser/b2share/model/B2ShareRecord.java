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
 * Content of a B2Share record
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2ShareRecord {

    public String id;
    public Date created;
    @JsonProperty("updated")
    public Date modified;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<B2ShareItem> files;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> links;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public B2ShareMetadata metadata;

    public B2ShareRecord() {
        this.files = new ArrayList<>();
        this.links = new HashMap<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public class B2ShareMetadata {

        @JsonProperty("$schema")
        public String schema;

        @JsonProperty("DOI")
        public String doi;

        @JsonProperty("ePIC_PID")
        public String ePIC;

        public String community;
        public String language;
        public String publisher;
        public String publication_state;
        public String contact_email;
        public String version;
        public boolean open_access;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<B2ShareTitle> titles;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<B2ShareDescription> descriptions;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<B2ShareCreator> creators;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<B2ShareContributor> contributors;

        @JsonProperty("license")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public Map<String, String> licenses;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<String> disciplines;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<String> keywords;

        public B2ShareMetadata() {
            this.titles = new ArrayList<>();
            this.descriptions = new ArrayList<>();
            this.creators = new ArrayList<>();
            this.contributors = new ArrayList<>();
            this.licenses = new HashMap<>();
            this.disciplines = new ArrayList<>();
            this.keywords = new ArrayList<>();
        }
    }

}
