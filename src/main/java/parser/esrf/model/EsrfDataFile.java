package parser.esrf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;


/**
 * ESRF file
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EsrfDataFile {

    public Info Datafile;

    /***
     * Constructor
     */
    public EsrfDataFile() {}

    /***
     * The details of the ESRF file
     */
    public class Info {
        public String id;
        public String name;
        public Date createTime;
        public Date modTime;
        public long fileSize;
        public String location;
    }
}
