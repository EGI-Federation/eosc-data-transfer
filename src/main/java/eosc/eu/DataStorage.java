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
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;


/***
 * Class for operations and queries about files and folders.
 * Dynamically selects the appropriate data transfer service, depending on the desired destination.
 */
@Path("/")
@SecuritySchemes(value = {
    @SecurityScheme(securitySchemeName = "none"),
    @SecurityScheme(securitySchemeName = "bearer",
            type = SecuritySchemeType.HTTP,
            scheme = "Bearer")} )
@Produces(MediaType.APPLICATION_JSON)
public class DataStorage extends DataTransferBase {

    @Inject
    ServicesConfig config;

    private static final Logger LOG = Logger.getLogger(DataStorage.class);


    /***
     * Constructor
     */
    public DataStorage() {
        super(LOG);
    }

    /**
     * Get the details of a file.
     * @param auth The access token needed to call the service.
     * @param seUrl The link to the file to get details of.
     * @return API Response, wraps an ActionSuccess(StorageElement) or an ActionError entity
     */
    @GET
    @Path("/storage/file")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "getFileInfo",  summary = "Retrieve information about a file in a storage system")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = StorageElement.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Not authorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Not authenticated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Storage element not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response getFileInfo(@RestHeader("Authorization") String auth,
                                @RestQuery("seUrl") @Parameter(required = true, description = "URL to the storage element (file) to get stats for")
                                String seUrl) {

        LOG.infof("Get details of storage element %s", seUrl);

        try {
            ActionParameters ap = new ActionParameters();
            CompletableFuture<Response> response = new CompletableFuture<>();
            Uni<ActionParameters> start = Uni.createFrom().item(ap);
            start
                .chain(params -> {
                    // Pick transfer service and create REST client for it
                    if (!getTransferService(params)) {
                        // Could not get REST client
                        response.complete(new ActionError("invalidServiceConfig",
                                                Tuple2.of("destination", params.destination)).toResponse());
                        return Uni.createFrom().failure(new RuntimeException());
                    }

                    return Uni.createFrom().item(params);
                })
                .chain(params -> {
                    // Get user info
                    return params.ts.getStorageElementInfo(auth, seUrl);
                })
                .chain(seinfo -> {
                    // Got file info
                    LOG.infof("Got info for %s %s", seinfo.isFolder ? "folder" : "file", seUrl);

                    // Success
                    response.complete(Response.ok(seinfo).build());
                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.error("Failed to get user info");
                    if (!response.isDone())
                        response.complete(new ActionError(e,
                                                Tuple2.of("destination", config.destination())).toResponse());
                })
                .subscribe().with(unused -> {});

            // Wait until user info is retrieved (possibly with error)
            Response r = response.get();
            return r;
        } catch (InterruptedException e) {
            // Cancelled
            return new ActionError("getFileInfoInterrupted").toResponse();
        } catch (ExecutionException e) {
            // Execution error
            return new ActionError("getFileInfoExecutionError").toResponse();
        }
    }

    /**
     * Get the details of a folder.
     * @param auth The access token needed to call the service.
     * @param seUrl The link to the folder to get details of.
     * @return API Response, wraps an ActionSuccess(StorageElement) or an ActionError entity
     */
    @GET
    @Path("/storage/folder")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "getFolderInfo",  summary = "Retrieve information about a folder in a storage system")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = StorageElement.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Not authorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Not authenticated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Storage element not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response getFolderInfo(@RestHeader("Authorization") String auth,
                                @RestQuery("seUrl") @Parameter(required = true, description = "URL to the storage element (folder) to get stats for")
                                String seUrl) {
        // This is the same for files and folders
        return getFileInfo(auth, seUrl);
    }

}
