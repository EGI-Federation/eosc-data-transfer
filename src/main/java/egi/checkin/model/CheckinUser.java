package egi.checkin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;


/**
 * Details of a Check-in user
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckinUser {

    public final static String ATTR_USERID = "userID";
    public final static String ATTR_USERNAME = "userName";
    public final static String ATTR_FIRSTNAME = "firstName";
    public final static String ATTR_LASTNAME = "lastName";
    public final static String ATTR_FULLNAME = "fullName";
    public final static String ATTR_EMAIL = "email";
    public final static String ATTR_EMAILCHECKED = "emailVerified";
    public final static String ATTR_ASSURANCE = "assurance";
    public final static String ATTR_ROLES = "roles";
    public final static String ATTR_GROUPS = "groups";

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("sub")
    public String subject;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("voperson_id")
    public String userId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("preferred_username")
    public String userName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String fullName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("given_name")
    public String firstName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("family_name")
    public String lastName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String email;

    @JsonProperty("email_verified")
    public boolean emailIsVerified = false;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("eduperson_assurance")
    public List<String> assurances;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> entitlements;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Set<String>> roles;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<String> groups;


    /***
     * Constructor
     */
    public CheckinUser() {}

    /***
     * Construct with user ID
     */
    public CheckinUser(String userId) { this.userId = userId; }

    /***
     * Construct and return full name of the user
     * @return Full name of the user
     */
    public String getFullName() {
        if(null == this.fullName) {
            if(null != this.firstName)
                this.fullName = this.firstName;
            if(null != this.lastName) {
                if(null != this.fullName && !this.fullName.isBlank())
                    this.fullName += " ";
                this.fullName += this.lastName;
            }
        }

        return this.fullName;
    }

    public String getUserId() {
        if(null == this.userId)
            this.userId = this.subject;
        return this.userId;
    }

    public CheckinUser setUserId(String userId) { this.userId = userId; return this; }
    public CheckinUser setFirstName(String firstName) { this.firstName = firstName; return this; }
    public CheckinUser setLastName(String lastName) { this.lastName = lastName; return this; }
    public CheckinUser setFullName(String fullName) { this.fullName = fullName; return this; }
    public CheckinUser setEmail(String email) { this.email = email; return this; }

    /***
     * Store another level of assurance (LoA)
     * @param loa The assurance
     * @return Ourselves, to allow chaining calls with .
     */
    public CheckinUser addAssurance(String loa) {
        if(null == this.assurances)
            this.assurances = new ArrayList<>();

        this.assurances.add(loa);

        return this;
    }

    /***
     * Store another entitlement
     * @param entitlement The entitlement
     * @return Ourselves, to allow chaining calls with .
     */
    public CheckinUser addEntitlement(String entitlement) {
        if(null == this.entitlements)
            this.entitlements = new ArrayList<>();

        this.entitlements.add(entitlement);

        return this;
    }

    /***
     * Store another virtual organization
     * @param voName The virtual organization
     * @return Ourselves, to allow chaining calls with .
     */
    public CheckinUser addVirtualOrganization(String voName) {
        if(null == this.roles)
            this.roles = new HashMap<>();

        if(!this.roles.containsKey(voName))
            this.roles.put(voName, new HashSet<>());

        return this;
    }

    /***
     * Store another role
     * @param voName The virtual organization
     * @param role The role
     * @return Ourselves, to allow chaining calls with .
     */
    public CheckinUser addRole(String voName, String role) {
        if(null == this.roles)
            this.roles = new HashMap<>();

        var vo = this.roles.get(voName);
        if(null == vo) {
            vo = new HashSet<>();
            this.roles.put(voName, vo);
        }

        vo.add(role);

        return this;
    }

    /***
     * Store another group
     * @param groupName The group name
     * @return Ourselves, to allow chaining calls with .
     */
    public CheckinUser addGroup(String groupName) {
        if(null == this.groups)
            this.groups = new HashSet<>();

        this.groups.add(groupName);

        return this;
    }

    /***
     * Serialize ourselves to JSON
     * @return JSON string, empty string on error
     */
    public String toJsonString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {}

        return "";
    }
}
