package parser.zenodo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Details of a file from a Zenodo record
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZenodoFile {

    public String id;
    public String key;
    public long size;
    public String type;
    public String checksum;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> links;

    public ZenodoFile() {}

    public String getMediaType() {
        if(null == this.type || this.type.isEmpty()) {
            Pattern p = Pattern.compile(".*\\.([a-z0-9]+)$", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(this.key);
            if(m.matches())
                this.type = m.group(1);
        }
        if(this.type.equals("gz")) {
            Pattern p = Pattern.compile("[^\\.]+\\.([a-z0-9]+)\\.gz$", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(this.key);
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
