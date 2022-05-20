package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;


/**
 * Details of the current user
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfo {

    public String kind = "UserInfo";
    public String base_id;
    public String delegation_id;
    public String user_dn;
    public List<String> dn;
    public List<String> vos;
    public List<String> vos_id;
    public List<String> voms_cred;

    public UserInfo() {}

    public UserInfo(egi.fts.model.UserInfo ui) {
        this.base_id = ui.base_id;
        this.delegation_id = ui.delegation_id;
        this.user_dn = ui.user_dn;

        if(null != ui.dn) {
            this.dn = new ArrayList<>();
            this.dn.addAll(ui.dn);
        }

        if(null != ui.vos) {
            this.vos = new ArrayList<>();
            this.vos.addAll(ui.vos);
        }

        if(null != ui.vos_id) {
            this.vos_id = new ArrayList<>();
            this.vos_id.addAll(ui.vos_id);
        }

        if(null != ui.voms_cred) {
            this.voms_cred = new ArrayList<>();
            this.voms_cred.addAll(ui.voms_cred);
        }
    }
}
