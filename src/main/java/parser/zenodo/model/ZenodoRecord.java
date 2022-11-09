package parser.zenodo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Content of a Zenodo record
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZenodoRecord {

    public String title;
    public String id;
    public String doi;
    public Date created;
    public Date modified;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ZenodoMetadata metadata;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<ZenodoFile> files;

    public ZenodoRecord() {
        files = new ArrayList<>();
    }

    public class ZenodoMetadata {
        public String version;
        public String access_right;
        public String language;
        public String license;
    }
}
