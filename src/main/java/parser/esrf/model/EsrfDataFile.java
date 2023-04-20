package parser.esrf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
     * Build URL to access/download this file
     * @param baseUrl is the base URL to the ESRF API
     * @param sessionId is the session to use to download the file (thus the built URL will expire after a while)
     * @return URL to access file content
     */
    public String accessUrl(String baseUrl, String sessionId) {
        return String.format("%s/catalogue/%s/data/download?datafileIds=%s", baseUrl, sessionId, Datafile.id);
    }

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
