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

import java.util.Arrays;
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
     * List the content of a folder.
     * @param auth The access token needed to call the service.
     * @param folderUrl The link to the folder to list content of.
     * @return API Response, wraps an ActionSuccess(StorageContent) or an ActionError entity
     */
    @GET
    @Path("/storage/folder/list")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "listFolderContent",  summary = "List the content of a folder from a storage system")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = StorageContent.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters/configuration or the storage element is not a folder",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Not authorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Storage element not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response listFolderContent(@RestHeader("Authorization") String auth,
                                @RestQuery("folderUrl") @Parameter(required = true, description = "URL to the storage element (folder) to list content of")
                                String folderUrl,
                                @RestQuery("dest") @DefaultValue(defaultDestination)
                                @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                                String destination) {

        LOG.infof("List content of folder %s", folderUrl);

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
                        // List folder content
                        return params.ts.listFolderContent(auth, folderUrl);
                    })
                    .chain(content -> {
                        // Got folder content
                        LOG.infof("Found %d element(s) in folder %s", content.count, folderUrl);

                        // Success
                        response.complete(Response.ok(content).build());
                        return Uni.createFrom().nullItem();
                    })
                    .onFailure().invoke(e -> {
                        LOG.errorf("Failed to list content of folder %s", folderUrl);
                        if (!response.isDone())
                            response.complete(new ActionError(e, Arrays.asList(
                                    Tuple2.of("folderUrl", folderUrl),
                                    Tuple2.of("destination", destination)) ).toResponse());
                    })
                    .subscribe().with(unused -> {});

            // Wait until folder content is listed (possibly with error)
            Response r = response.get();
            return r;
        } catch (InterruptedException e) {
            // Cancelled
            return new ActionError("listFolderContentInterrupted").toResponse();
        } catch (ExecutionException e) {
            // Execution error
            return new ActionError("listFolderContentExecutionError").toResponse();
        }
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
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Storage element not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response getFileInfo(@RestHeader("Authorization") String auth,
                                @RestQuery("seUrl") @Parameter(required = true, description = "URL to the storage element (file) to get stats for")
                                String seUrl,
                                @RestQuery("dest") @DefaultValue(defaultDestination)
                                @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                                String destination) {

        LOG.infof("Get details of storage element %s", seUrl);

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
                    // Get storage element info
                    return params.ts.getStorageElementInfo(auth, seUrl);
                })
                .chain(seinfo -> {
                    // Got storage element info
                    LOG.infof("Got info for %s %s", seinfo.isFolder ? "folder" : "file", seUrl);

                    // Success
                    response.complete(Response.ok(seinfo).build());
                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.errorf("Failed to get info about storage element %s", seUrl);
                    if (!response.isDone())
                        response.complete(new ActionError(e, Arrays.asList(
                                                Tuple2.of("seUrl", seUrl),
                                                Tuple2.of("destination", destination)) ).toResponse());
                })
                .subscribe().with(unused -> {});

            // Wait until storage element info is retrieved (possibly with error)
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
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Storage element not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response getFolderInfo(@RestHeader("Authorization") String auth,
                                  @RestQuery("seUrl") @Parameter(required = true, description = "URL to the storage element (folder) to get stats for")
                                  String seUrl,
                                  @RestQuery("dest") @DefaultValue(defaultDestination)
                                  @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                                  String destination) {
        // This is the same for files and folders
        return getFileInfo(auth, seUrl, destination);
    }

    /**
     * Create a new folder.
     * @param auth The access token needed to call the service.
     * @param seUrl The link to the folder to create.
     * @return API Response, wraps an ActionError entity in case of error
     */
    @POST
    @Path("/storage/folder")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "createFolder",  summary = "Create new folder in a storage system")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success"),
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
    public Response createFolder(@RestHeader("Authorization") String auth,
                                 @RestQuery("seUrl") @Parameter(required = true, description = "URL to the storage element (folder) to create")
                                 String seUrl,
                                 @RestQuery("dest") @DefaultValue(defaultDestination)
                                 @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                                 String destination) {

        LOG.infof("Create folder %s", seUrl);

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
                    // Create folder
                    return params.ts.createFolder(auth, seUrl);
                })
                .chain(created -> {
                    // Folder got created
                    LOG.infof("Created folder %s (%s)", seUrl, created);

                    // Success
                    response.complete(Response.ok().build());
                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.errorf("Failed to create folder %s", seUrl);
                    if (!response.isDone())
                        response.complete(new ActionError(e, Arrays.asList(
                                Tuple2.of("seUrl", seUrl),
                                Tuple2.of("destination", destination)) ).toResponse());
                })
                .subscribe().with(unused -> {});

            // Wait until folder is created (possibly with error)
            Response r = response.get();
            return r;
        } catch (InterruptedException e) {
            // Cancelled
            return new ActionError("createFolderInterrupted").toResponse();
        } catch (ExecutionException e) {
            // Execution error
            return new ActionError("createFolderExecutionError").toResponse();
        }
    }

    /**
     * Delete existing folder.
     * @param auth The access token needed to call the service.
     * @param seUrl The link to the folder to delete.
     * @return API Response, wraps an ActionError entity in case of error
     */
    @DELETE
    @Path("/storage/folder")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "deleteFolder",  summary = "Delete existing folder from a storage system")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success"),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Not authorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Folder not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response deleteFolder(@RestHeader("Authorization") String auth,
                                 @RestQuery("seUrl") @Parameter(required = true, description = "URL to the storage element (folder) to delete")
                                 String seUrl,
                                 @RestQuery("dest") @DefaultValue(defaultDestination)
                                 @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                                 String destination) {

        LOG.infof("Delete folder %s", seUrl);

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
                    // Delete folder
                    return params.ts.deleteFolder(auth, seUrl);
                })
                .chain(deleted -> {
                    // Folder got deleted
                    LOG.infof("Deleted folder %s (%s)", seUrl, deleted);

                    // Success
                    response.complete(Response.ok().build());
                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.errorf("Failed to delete folder %s", seUrl);
                    if (!response.isDone())
                        response.complete(new ActionError(e, Arrays.asList(
                                Tuple2.of("seUrl", seUrl),
                                Tuple2.of("destination", destination)) ).toResponse());
                })
                .subscribe().with(unused -> {});

            // Wait until folder is deleted (possibly with error)
            Response r = response.get();
            return r;
        } catch (InterruptedException e) {
            // Cancelled
            return new ActionError("deleteFolderInterrupted").toResponse();
        } catch (ExecutionException e) {
            // Execution error
            return new ActionError("deleteFolderExecutionError").toResponse();
        }
    }

    /**
     * Rename a file.
     * @param auth The access token needed to call the service.
     * @param operation The links to the old and new storage element URLs.
     * @return API Response, wraps an ActionError entity in case of error
     */
    @PUT
    @Path("/storage/file")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "renameFile",  summary = "Rename existing file in a storage system")
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success"),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Not authorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="File not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response renameFile(@RestHeader("Authorization") String auth, StorageRenameOperation operation,
                               @RestQuery("dest") @DefaultValue(defaultDestination)
                               @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                               String destination) {

        if(null != operation.seUrlOld && null != operation.seUrlNew)
            LOG.infof("Renaming storage element %s to %s", operation.seUrlOld, operation.seUrlNew);

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
                    // Rename storage element
                    return params.ts.renameStorageElement(auth, operation.seUrlOld, operation.seUrlNew);
                })
                .chain(renamed -> {
                    // Storage element got renamed
                    LOG.infof("Renamed storage element %s to %s (%s)", operation.seUrlOld, operation.seUrlNew, renamed);

                    // Success
                    response.complete(Response.ok().build());
                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.errorf("Failed to rename storage element %s", operation.seUrlOld);
                    if (!response.isDone())
                        response.complete(new ActionError(e, Arrays.asList(
                                Tuple2.of("seUrl", operation.seUrlOld),
                                Tuple2.of("seUrlNew", operation.seUrlNew),
                                Tuple2.of("destination", destination)) ).toResponse());
                })
                .subscribe().with(unused -> {});

            // Wait until storage element is renamed (possibly with error)
            Response r = response.get();
            return r;
        } catch (InterruptedException e) {
            // Cancelled
            return new ActionError("renameFileInterrupted").toResponse();
        } catch (ExecutionException e) {
            // Execution error
            return new ActionError("renameFileExecutionError").toResponse();
        }
    }

    /**
     * Rename a folder.
     * @param auth The access token needed to call the service.
     * @param operation The links to the old and new storage element URLs.
     * @return API Response, wraps an ActionError entity in case of error
     */
    @PUT
    @Path("/storage/folder")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "renameFolder",  summary = "Rename existing folder in a storage system")
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success"),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Not authorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Folder not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response renameFolder(@RestHeader("Authorization") String auth,
                                 StorageRenameOperation operation,
                                 @RestQuery("dest") @DefaultValue(defaultDestination)
                                 @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                                 String destination) {
        // This is the same for files and folders
        return renameFile(auth, operation, destination);
    }

}
