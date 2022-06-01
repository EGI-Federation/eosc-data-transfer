package eosc.eu;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import eosc.eu.model.*;
import org.jboss.resteasy.reactive.RestQuery;


/***
 * Class for user queries.
 * Dynamically selects the appropriate data transfer service, depending on the desired destination.
 */
@Path("/")
@SecuritySchemes(value = {
    @SecurityScheme(securitySchemeName = "none"),
    @SecurityScheme(securitySchemeName = "bearer",
            type = SecuritySchemeType.HTTP,
            scheme = "Bearer")} )
@Produces(MediaType.APPLICATION_JSON)
public class DataTransferUser extends DataTransferBase {

    @Inject
    ServicesConfig config;

    private static final Logger LOG = Logger.getLogger(DataTransferUser.class);


    /***
     * Constructor
     */
    public DataTransferUser() {
        super(LOG);
    }

    /**
     * Retrieve information about current user.
     * @param auth The access token needed to call the service.
     * @return API Response, wraps an ActionSuccess(UserInfo) or an ActionError entity
     */
    @GET
    @Path("/user/info")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "getUserInfo",  summary = "Retrieve information about authenticated user")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UserInfo.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Not authorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response getUserInfo(@RestHeader("Authorization") String auth,
                                @RestQuery("dest") @DefaultValue(defaultDestination)
                                @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                                String destination) {

        LOG.info("Get current user info");

        try {
            ActionParameters ap = new ActionParameters(destination);
            CompletableFuture<Response> response = new CompletableFuture<>();
            Uni<ActionParameters> start = Uni.createFrom().item(ap);
            start
                .chain(params -> {
                    // Pick transfer service and create REST client for it
                    if (!getTransferService(params)) {
                        // Could not get REST client
                        response.complete(new ActionError("invalidServiceConfig",
                                                Tuple2.of("destination", destination)).toResponse());
                        return Uni.createFrom().failure(new RuntimeException());
                    }

                    return Uni.createFrom().item(params);
                })
                .chain(params -> {
                    // Get user info
                    return params.ts.getUserInfo(auth);
                })
                .chain(userinfo -> {
                    // Got user info
                    LOG.infof("Got user info for user_dn:%s", userinfo.user_dn);

                    // Success
                    response.complete(Response.ok(userinfo).build());
                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.error("Failed to get user info");
                    if (!response.isDone())
                        response.complete(new ActionError(e,
                                                Tuple2.of("destination", destination)).toResponse());
                })
                .subscribe().with(unused -> {});

            // Wait until user info is retrieved (possibly with error)
            Response r = response.get();
            return r;
        } catch (InterruptedException e) {
            // Cancelled
            return new ActionError("getUserInfoInterrupted").toResponse();
        } catch (ExecutionException e) {
            // Execution error
            return new ActionError("getUserInfoExecutionError").toResponse();
        }
    }

}
