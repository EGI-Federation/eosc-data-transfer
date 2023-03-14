package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;


/**
 * Details of the current user
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfo {

    public String kind = "UserInfo";
    public String base_id;
    public String user_dn;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String delegation_id;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> dn;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="Virtual organisation name(s)")
    public List<String> vos;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="Virtual organisation identifier(s)")
    public List<String> vos_id;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> voms_cred;


    /**
     * Constructor
     */
    public UserInfo() {}

    /**
     * Construct from FTS user info, makes deep copy
     */
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
