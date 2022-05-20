package egi.fts.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;


/**
 * Details of the current user
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfo {

    public String base_id;
    public String delegation_id;
    public String user_dn;
    public List<String> dn;
    public List<String> vos;
    public List<String> vos_id;
    public List<String> voms_cred;


    /**
     * Constructor
     */
    public UserInfo() {}
}
