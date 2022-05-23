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
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import eosc.eu.model.*;
import egi.fts.FileTransferService;
import egi.fts.model.Job;


@Path("/")
@SecuritySchemes(value = {
    @SecurityScheme(securitySchemeName = "none"),
    @SecurityScheme(securitySchemeName = "bearer",
            type = SecuritySchemeType.HTTP,
            scheme = "Bearer")} )
@Produces(MediaType.APPLICATION_JSON)
public class TransferServiceProxy {

    @Inject
    ServicesConfig config;

    private static final Logger LOG = Logger.getLogger(TransferServiceProxy.class);


    /**
     * Prepare REST client for the data transfer service.
     *
     * @param params dictates which transfer service we pick, mapping is in the configuration file
     * @return true on success, updates fields "destination", "transferService" and "fts"
     */
    @PostConstruct
    private boolean getTransferService(ActionParameters params) {

        LOG.info("Obtaining REST client for transfer service");

        if (null != params.fts)
            return true;

        params.destination = config.destination();
        LOG.infof("Destination is <%s>", params.destination);

        String serviceId = config.destinations().get(params.destination);
        if (null == serviceId) {
            // Unsupported destination
            LOG.errorf("No transfer service configured for destination <%s>", params.destination);
            return false;
        }

        ServicesConfig.TransferServiceConfig serviceConfig = config.services().get(serviceId);
        if (null == serviceConfig) {
            // Unsupported transfer service
            LOG.errorf("No configuration found for transfer service <%s>", serviceId);
            return false;
        }

        params.transferService = serviceConfig.name();
        LOG.infof("Transfer with <%s>", params.transferService);

        // Check if transfer service base URL is valid
        URL urlTransferService;
        try {
            urlTransferService = new URL(serviceConfig.url());
        } catch (MalformedURLException e) {
            LOG.error(e.getMessage());
            return false;
        }

        // Get the class of the transfer service we should use
        try {
            var classType = Class.forName(serviceConfig.className());
            // TODO: Load class dynamically

            // Create the REST client for the selected transfer service
            params.fts = RestClientBuilder.newBuilder()
                    .baseUrl(urlTransferService)
                    .build(FileTransferService.class);

            return true;
        } catch (ClassNotFoundException e) {
            LOG.error(e.getMessage());
        }

        return false;
    }

    /**
     * Retrieve information about current user.
     *
     * @return API Response, wraps an ActionSuccess or an ActionError entity
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
            @APIResponse(responseCode = "403", description="Not authenticated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response getUserInfo(@RestHeader("Authorization") String auth) {

        LOG.info("Get current user info");

        try {
            ActionParameters ap = new ActionParameters(auth);
            CompletableFuture<Response> response = new CompletableFuture<>();
            Uni<ActionParameters> start = Uni.createFrom().item(ap);
            start
                .onItem().transformToUni(params -> {
                    // Pick transfer service and create REST client for it
                    if (!getTransferService(params)) {
                        // Could not get REST client
                        response.complete(new ActionError("invalidServiceConfig",
                                                Tuple2.of("destination", params.destination)).toResponse());
                        return Uni.createFrom().failure(new RuntimeException());
                    }

                    return Uni.createFrom().item(params);
                })
                .onItem().transformToUni(params -> {
                    // Get user info
                    return params.fts.getUserInfoAsync(params.authorization);
                })
                .onItem().transformToUni(userinfo -> {
                    // Got user info
                    LOG.infof("Got user info for user_dn:%s", userinfo.user_dn);

                    // Success
                    response.complete(Response.ok(new eosc.eu.model.UserInfo(userinfo)).build());
                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.error("Failed to get user info");
                    if (!response.isDone())
                        response.complete(new ActionError(e, Tuple2.of("destination", config.destination()))
                                .toResponse());
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

    /**
     * Initiate new transfer of multiple sets of files.
     *
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @POST
    @Path("/transfer")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "startTransfer",  summary = "Initiate new transfer of multiple sets of files")
    @APIResponses(value = {
            @APIResponse(responseCode = "202", description = "Accepted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransferInfo.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Not authorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Not authenticated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response startTransfer(@RestHeader("Authorization") String auth, Transfer transfer) {

        LOG.info("Start new data transfer");

        try {
            ActionParameters ap = new ActionParameters(auth);
            CompletableFuture<Response> response = new CompletableFuture<>();
            Uni<ActionParameters> start = Uni.createFrom().item(ap);
            start
                .onItem().transformToUni(params -> {
                    // Pick transfer service and create REST client for it
                    if (!getTransferService(params)) {
                        // Could not get REST client
                        response.complete(new ActionError("invalidServiceConfig",
                                Tuple2.of("destination", params.destination)).toResponse());
                        return Uni.createFrom().failure(new RuntimeException());
                    }

                    return Uni.createFrom().item(params);
                })
                .onItem().transformToUni(params -> {
                    // Start transfer
                    Job job = new Job(transfer);
                    return params.fts.startTransferAsync(params.authorization, job);
                })
                .onItem().transformToUni(jobinfo -> {
                    // Transfer started
                    LOG.infof("Started new transfer %s", jobinfo.job_id);

                    // Success
                    response.complete(Response.accepted(new TransferInfo(jobinfo)).build());
                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.error("Failed to start new transfer");
                    if (!response.isDone())
                        response.complete(new ActionError(e, Tuple2.of("destination", config.destination()))
                                .toResponse());
                })
                .subscribe().with(unused -> {});

            // Wait until transfer is started (possibly with error)
            Response r = response.get();
            return r;
        } catch (InterruptedException e) {
            // Cancelled
            return new ActionError("startTransferInterrupted").toResponse();
        } catch (ExecutionException e) {
            // Execution error
            return new ActionError("startTransferExecutionError").toResponse();
        }
    }

}
