package eosc.eu;

import egi.checkin.model.CheckinUser;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.NoCache;
import io.smallrye.mutiny.Uni;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.Authenticated;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import eosc.eu.model.*;


/***
 * Class for user queries.
 * Dynamically selects the appropriate data transfer service, depending on the desired destination.
 */
@Path("/user")
@Produces(MediaType.APPLICATION_JSON)
public class DataTransferUser extends DataTransferBase {

    private static final Logger log = Logger.getLogger(DataTransferUser.class);

    @Inject
    TransferConfig config;

    @Inject
    MeterRegistry registry;

    @Inject
    SecurityIdentity identity;


    /***
     * Construct with meter
     */
    public DataTransferUser() { super(log); }

    /**
     * Retrieve information about current user.
     * @param auth The access token needed to call the service.
     * @return API Response, wraps an ActionSuccess(UserInfo) or an ActionError entity
     */
    @GET
    @Path("/info")
    @NoCache
    @SecurityRequirement(name = "OIDC")
    @Authenticated
    @Operation(operationId = "getUserInfo",  summary = "Retrieve information about authenticated user")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = UserInfo.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> getUserInfo(@RestHeader(HttpHeaders.AUTHORIZATION) String auth) {

        final var oidcAttributes = identity.getAttributes();
        MDC.put("callerId", oidcAttributes.get(CheckinUser.ATTR_USERID));

        log.info("Getting current user info");

        Uni<Response> result = Uni.createFrom().item(oidcAttributes)

            .chain(attributes -> {
                // Got attributes from the access token
                var userinfo = new UserInfo(attributes);

                log.info("Got user info");
                return Uni.createFrom().item(Response.ok(userinfo).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to get user info");
                return new ActionError(e).toResponse();
            });

        return result;
    }

}
