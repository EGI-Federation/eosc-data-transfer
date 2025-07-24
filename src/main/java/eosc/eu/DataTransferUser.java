package eosc.eu;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import eosc.eu.model.*;
import eosc.eu.model.Transfer.Destination;



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


    /***
     * Construct with meter
     */
    public DataTransferUser() { super(log); }

    /**
     * Retrieve information about current user.
     * @param auth The access token needed to call the service.
     * @param destination The type of destination storage (selects transfer service to call).
     * @return API Response, wraps an ActionSuccess(UserInfo) or an ActionError entity
     */
    @GET
    @Path("/info")
    @SecurityRequirement(name = "OIDC")
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
    public Uni<Response> getUserInfo(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                     @RestQuery("dest") @DefaultValue(DEFAULT_DESTINATION)
                                     @Parameter(schema = @Schema(implementation = Destination.class),
                                                description = "The destination storage")
                                     String destination) {

        MDC.put("dest", destination);

        log.info("Getting current user info");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Pick transfer service and create REST client for it
                var params = new ActionParameters(destination);
                if (!getTransferService(params)) {
                    // Could not get REST client
                    return Uni.createFrom().failure(new TransferServiceException("invalidServiceConfig"));
                }

                return Uni.createFrom().item(params);
            })
            .chain(params -> {
                // Get user info
                return params.ts.getUserInfo(auth);
            })
            .chain(userinfo -> {
                // Got user info, success
                log.info("Got user info");
                return Uni.createFrom().item(Response.ok(userinfo).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to get user info");
                return new ActionError(e, Tuple2.of("destination", destination)).toResponse();
            });

        return result;
    }

}
