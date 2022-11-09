package parser.b2share.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Details of a file from a B2Share record
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2ShareFile {

    @JsonProperty("version_id")
    public String versionId;

    @JsonProperty("key")
    public String name;
    public long size;
    public String checksum;
    public Date created;

    @JsonProperty("updated")
    public Date modified;

    @JsonProperty("mimetype")
    public String type;

    @JsonProperty("is_head")
    public boolean isHead;

    @JsonProperty("delete_marker")
    public boolean markedForDeletion;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> links;

    public B2ShareFile() {}

    public String getMediaType() {
        if(null == this.type || this.type.isEmpty()) {
            Pattern p = Pattern.compile(".*\\.([a-z0-9]+)$", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(this.name);
            if(m.matches())
                this.type = m.group(1);
        }
        if(this.type.equals("gz")) {
            Pattern p = Pattern.compile("[^\\.]+\\.([a-z0-9]+)\\.gz$", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(this.name);
            if(m.matches())
                this.type = m.group(1);
        }

        switch (this.type) {
            case "csv": return "application/csv";
            case "zip": return "application/zip";
            case "tsv": return "application/tab-separated-values";
        }

        return this.type;
    }
}
