package parser.esrf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


/**
 * Credentials for ESRF
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EsrfCredentials {

    public String username;
    public String password;
    public String plugin = "db";

    /***
     * Create from credentials
     * @param username ESRF username
     * @param password Password for the user
     */
    public EsrfCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
