package eosc.eu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.oidc.UserInfo;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import egi.checkin.model.CheckinUser;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;


/***
 * Class to customize role identification from the user information
 * See also https://quarkus.io/guides/security-customization#security-identity-customization
 */
@ApplicationScoped
public class RolesCustomization implements SecurityIdentityAugmentor {

    private static final Logger log = Logger.getLogger(RolesCustomization.class);


    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        // NOTE: In case role parsing is a blocking operation, replace with the line below
        // return context.runBlocking(this.build(identity));
        return Uni.createFrom().item(this.build(identity));
    }

    private Supplier<SecurityIdentity> build(SecurityIdentity identity) {
        if(identity.isAnonymous()) {
            return () -> identity;
        } else {
            // Create a new builder and copy principal, attributes, credentials and roles from the original identity
            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);

            log.debug("Building security identity");

            // Extract the OIDC user information, loaded due to the setting quarkus.roles.source=userinfo
            var ui = identity.getAttribute("userinfo");
            var isAJO = ui instanceof UserInfo;
            if(null != ui && (isAJO || ui instanceof String)) {
                // Construct Check-in UserInfo from the user info fetched by OIDC
                CheckinUser userInfo = null;
                String json = null;
                try {
                    var mapper = new ObjectMapper();
                    json = isAJO ? ((UserInfo)ui).getJsonObject().toString() : ui.toString();
                    userInfo = mapper.readValue(json, CheckinUser.class);

                    if(null != userInfo.subject)
                        builder.addAttribute(CheckinUser.ATTR_USERID, userInfo.subject);
                    else if(null != userInfo.userId)
                        builder.addAttribute(CheckinUser.ATTR_USERID, userInfo.userId);

                    if(null != userInfo.userName)
                        builder.addAttribute(CheckinUser.ATTR_USERNAME, userInfo.userName);

                    if(null != userInfo.firstName)
                        builder.addAttribute(CheckinUser.ATTR_FIRSTNAME, userInfo.firstName);

                    if(null != userInfo.lastName)
                        builder.addAttribute(CheckinUser.ATTR_LASTNAME, userInfo.lastName);

                    if(null != userInfo.fullName || null != userInfo.firstName || null != userInfo.lastName)
                        builder.addAttribute(CheckinUser.ATTR_FULLNAME, userInfo.getFullName());

                    if(null != userInfo.email) {
                        builder.addAttribute(CheckinUser.ATTR_EMAIL, userInfo.email);
                        builder.addAttribute(CheckinUser.ATTR_EMAILCHECKED, userInfo.emailIsVerified);
                    }

                    if(null != userInfo.assurances) {
                        Pattern assuranceRex = Pattern.compile("^https?\\://(aai[^\\.]*.egi.eu)/LoA#([^\\:#/]+)");
                        for(var a : userInfo.assurances) {
                            var matcher = assuranceRex.matcher(a);
                            if(matcher.matches()) {
                                // Got an EGI Check-in backed assurance level
                                var assurance = matcher.group(2);
                                builder.addAttribute(CheckinUser.ATTR_ASSURANCE, assurance.toLowerCase());
                                break;
                            }
                        }
                    }

                    if(null != userInfo.entitlements) {
                        // Extract all groups and subgroups
                        final String vop = "^urn\\:mace\\:[^\\:]+\\:group\\:([^\\:]+)\\:role=member#[a-z0-9\\-\\.]+$";
                        final String rolep = "^urn\\:mace\\:[^\\:]+\\:group\\:([^\\:]+)\\:([^\\:]+)\\:role=member#[a-z0-9\\-\\.]+$";
                        final String groupp = "^urn\\:[^\\:]+\\:group\\:([^\\:]+)$";
                        Pattern vp = Pattern.compile(vop);
                        Pattern rp = Pattern.compile(rolep);
                        Pattern gp = Pattern.compile(groupp);

                        for(var e : userInfo.entitlements) {
                            Matcher m = vp.matcher(e);
                            if(m.matches()) {
                                // The user is member of a top level group (aka virtual organization)
                                final var voName = m.group(1);
                                userInfo.addVirtualOrganization(voName);
                            }

                            m = rp.matcher(e);
                            if(m.matches()) {
                                // The user is member of a subgroup (aka a role in a virtual organization)
                                final var voName = m.group(1);
                                final var roleName = m.group(2);
                                userInfo.addRole(voName, roleName);
                            }

                            m = gp.matcher(e);
                            if(m.matches()) {
                                // The user is member of a group
                                final var groupName = m.group(1);
                                userInfo.addGroup(groupName);
                            }
                        }

                        if(null != userInfo.roles && !userInfo.roles.isEmpty())
                            builder.addAttribute(CheckinUser.ATTR_ROLES, userInfo.roles);

                        if(null != userInfo.groups && !userInfo.groups.isEmpty())
                            builder.addAttribute(CheckinUser.ATTR_GROUPS, new ArrayList<>(userInfo.groups));
                    }
                }
                catch (JsonProcessingException ex) {
                    // Error deserializing JSON into CheckinUser instance
                    MDC.put("OIDC.userinfo", null != json ? json : "null");
                    log.warn("Cannot deserialize OIDC userinfo");
                }
            }

            return builder::build;
        }
    }
}
