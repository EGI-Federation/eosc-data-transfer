package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.*;

import egi.checkin.model.CheckinUser;


/**
 * Details of the current user
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Identity, virtual organisations and groups of the authenticated caller")
public class UserInfo {

    @Schema(description = "Type discriminator of this schema", example = "UserInfo")
    public String kind = "UserInfo";

    @Schema(description = "EGI Check-in identifier of the user",
            example = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6")
    public String userId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Given name", example = "Jane")
    public String firstName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Family name", example = "Doe")
    public String lastName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Full display name", example = "Jane Doe")
    public String fullName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Email address", example = "jane.doe@example.org")
    public String email;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="Virtual organisations and roles in them")
    public List<VirtualOrganization> vos;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Groups the user belongs to")
    public List<String> groups;


    /**
     * Constructor
     */
    public UserInfo() {}

    /**
     * Construct from OIDC attributes
     * @param attributes Contains the OIDC attributes after introspection
     */
    public UserInfo(Map<String, Object> attributes) {
        this.userId = (String)attributes.get(CheckinUser.ATTR_USERID);
        this.firstName = (String)attributes.get(CheckinUser.ATTR_FIRSTNAME);
        this.lastName = (String)attributes.get(CheckinUser.ATTR_LASTNAME);
        this.fullName = (String)attributes.get(CheckinUser.ATTR_FULLNAME);
        this.email = (String)attributes.get(CheckinUser.ATTR_EMAIL);

        final var r = attributes.get(CheckinUser.ATTR_ROLES);
        final var g = attributes.get(CheckinUser.ATTR_GROUPS);

        if(r instanceof Map) {
            var roles = (Map<String, Set<String>>)r;
            if(null != roles)
                for(Map.Entry<String, Set<String>> vo : roles.entrySet()) {
                    var voName = vo.getKey();
                    var voRoles = vo.getValue();
                    addVirtualOrganization(voName, null != voRoles ? new ArrayList<>(voRoles) : null);
                }
        }

        if(g instanceof Set) {
            var groups = (Set<String>)g;
            if(null != groups)
                for(var group : groups)
                    addGroup(group);
        }
    }

    /***
     * Store another virtual organization and the roles held in it.
     * Warning: Does not check if there is already such a virtual organization in the list.
     * @param voName The virtual organization
     * @param roles The roles held in the virtual organization
     * @return Ourselves, to allow chaining calls with .
     */
    public UserInfo addVirtualOrganization(String voName, List<String> roles) {
        if(null == this.vos)
            this.vos = new ArrayList<>();

        this.vos.add(new VirtualOrganization(voName, roles));

        return this;
    }

    /***
     * Store another group
     * @param groupName The group name
     * @return Ourselves, to allow chaining calls with .
     */
    public UserInfo addGroup(String groupName) {
        if(null == this.groups)
            this.groups = new ArrayList<>();

        this.groups.add(groupName);

        return this;
    }

    /**
     * Membership and roles in a virtual organization
     */
    @Schema(description = "A virtual organisation and the caller's roles in it")
    public static class VirtualOrganization {

        @Schema(description = "Name of the virtual organisation", example = "eosc-beyond.eu")
        public String name;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Schema(description = "Roles held in this virtual organisation", example = "[\"member\"]")
        public List<String> roles;


        /**
         * Constructor
         */
        public VirtualOrganization() {}

        /***
         * Construct with name
         * @param voName The virtual organization
         */
        public VirtualOrganization(String voName) {
            this.name = voName;
        }

        /**
         * Construct and initialize
         * @param voName The virtual organization
         * @param roles The roles held in the virtual organization
         */
        public VirtualOrganization(String voName, List<String> roles) {
            this.name = voName;

            if(null != roles) {
                this.roles = new ArrayList<>();
                this.roles.addAll(roles);
            }
        }
    }
}
