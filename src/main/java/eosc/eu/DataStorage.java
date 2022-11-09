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
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import eosc.eu.model.*;
import eosc.eu.model.Transfer.Destination;
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
    TransfersConfig config;

    private static final Logger LOG = Logger.getLogger(DataStorage.class);


    /***
     * Constructor
     */
    public DataStorage() {
        super(LOG);
    }

    /**
     * List all supported destination storage types, with info about each.
     * @return API Response, wraps an ActionSuccess(boolean) or an ActionError entity
     */
    @GET
    @Path("/storage/types")
    //@SecurityRequirement(name = "none")
    @Operation(operationId = "listSupportedStorages",  summary = "List all supported storage types")
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = StorageTypes.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> listStorageTypes() {

        LOG.infof("List supported storage types");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Iterate all configured storage types
                var storageTypes = new StorageTypes();
                for(var dKey : this.config.destinations().keySet()) {
                    var storageConfig = this.config.destinations().get(dKey);
                    var storageDescription = storageConfig.description().isPresent() ? storageConfig.description().get() :"";
                    var storageInfo = new StorageInfo(dKey, storageConfig.authType(), storageDescription);
                    storageTypes.add(storageInfo);
                }

                return Uni.createFrom().item(storageTypes.toResponse());
            })
            .onFailure().recoverWithItem(e -> {
                LOG.error("Failed to list supported storage destinations");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Check if browsing destination storage is supported and what auth type it requires.
     * @return API Response, wraps an ActionSuccess(boolean) or an ActionError entity
     */
    @GET
    @Path("/storage/info")
    //@SecurityRequirement(name = "none")
    @Operation(operationId = "getStorageInfo",  summary = "Retrieve information about destination storage")
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = StorageInfo.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> getStorageInfo(@RestQuery("dest") @DefaultValue(defaultDestination)
                                        @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                                        String destination) {

        LOG.infof("Retrieve information about a storage type %s?", destination);

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
                // Retrieve the authentication type of the destination storage
                var storageConfig = config.destinations().get(params.destination);
                if (null == storageConfig)
                    // Unsupported destination
                    return Uni.createFrom().failure(new TransferServiceException("invalidDestination"));

                // Check if browsing storage is supported
                var storageDescription = storageConfig.description().isPresent() ? storageConfig.description().get() :"";
                var storageInfo = new StorageInfo(destination,
                                                  storageConfig.authType(),
                                                  params.ts.canBrowseStorage(),
                                                  params.ts.getServiceName(),
                                                  storageDescription);

                LOG.infof("Destination storage %s does%s support browsing", destination,
                        (storageInfo.canBrowse.isPresent() && storageInfo.canBrowse.get()) ? "" : " not");

                return Uni.createFrom().item(storageInfo.toResponse());
            })
            .onFailure().recoverWithItem(e -> {
                LOG.errorf("Failed to retrieve info about storage type %s", destination);
                return new ActionError(e, Tuple2.of("destination", destination)).toResponse();
            });

        return result;
    }

    /**
     * List the content of a folder.
     * @param auth The access token needed to call the service.
     * @param folderUrl The link to the folder to list content of.
     * @param destination The type of destination storage.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
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
    public Uni<Response> listFolderContent(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                            @RestQuery("folderUrl") @Parameter(required = true, description = "URL to the storage element (folder) to list content of")
                            String folderUrl,
                            @RestQuery("dest") @DefaultValue(defaultDestination)
                            @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                            String destination,
                            @RestQuery("storageAuth") @Parameter(required = false, description = "Credentials for the destination storage, Base-64 encoded 'user:password'")
                            String storageAuth) {

        LOG.infof("List content of folder %s", folderUrl);

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
                // List folder content
                return params.ts.listFolderContent(auth, storageAuth, folderUrl);
            })
            .chain(content -> {
                // Got folder content
                LOG.infof("Found %d element(s) in folder %s", content.count, folderUrl);

                // Success
                return Uni.createFrom().item(Response.ok(content).build());
            })
            .onFailure().recoverWithItem(e -> {
                LOG.errorf("Failed to list content of folder %s", folderUrl);
                return new ActionError(e, Arrays.asList(
                             Tuple2.of("folderUrl", folderUrl),
                             Tuple2.of("destination", destination)) ).toResponse();
            });

        return result;
    }

    /**
     * Get the details of a file.
     * @param auth The access token needed to call the service.
     * @param seUrl The link to the file to get details of.
     * @param destination The type of destination storage.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
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
    public Uni<Response> getFileInfo(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                            @RestQuery("seUrl") @Parameter(required = true, description = "URL to the storage element (file) to get stats for")
                            String seUrl,
                            @RestQuery("dest") @DefaultValue(defaultDestination)
                            @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                            String destination,
                            @RestQuery("storageAuth") @Parameter(required = false, description = "Credentials for the destination storage, Base-64 encoded 'user:password'")
                            String storageAuth) {

        LOG.infof("Get details of storage element %s", seUrl);

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
                // Get storage element info
                return params.ts.getStorageElementInfo(auth, storageAuth, seUrl);
            })
            .chain(seinfo -> {
                // Got storage element info
                LOG.infof("Got info for %s %s", seinfo.isFolder ? "folder" : "file", seUrl);

                // Success
                return Uni.createFrom().item(Response.ok(seinfo).build());
            })
            .onFailure().recoverWithItem(e -> {
                LOG.errorf("Failed to get info about storage element %s", seUrl);
                return new ActionError(e, Arrays.asList(
                             Tuple2.of("seUrl", seUrl),
                             Tuple2.of("destination", destination)) ).toResponse();
            });

        return result;
    }

    /**
     * Get the details of a folder.
     * @param auth The access token needed to call the service.
     * @param seUrl The link to the folder to get details of.
     * @param destination The type of destination storage.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
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
    public Uni<Response> getFolderInfo(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                              @RestQuery("seUrl") @Parameter(required = true, description = "URL to the storage element (folder) to get stats for")
                              String seUrl,
                              @RestQuery("dest") @DefaultValue(defaultDestination)
                              @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                              String destination,
                              @RestQuery("storageAuth") @Parameter(required = false, description = "Credentials for the destination storage, Base-64 encoded 'user:password'")
                              String storageAuth) {
        // This is the same for files and folders
        return getFileInfo(auth, seUrl, destination, storageAuth);
    }

    /**
     * Create a new folder.
     * @param auth The access token needed to call the service.
     * @param seUrl The link to the folder to create.
     * @param destination The type of destination storage.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
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
    public Uni<Response> createFolder(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                             @RestQuery("seUrl") @Parameter(required = true, description = "URL to the storage element (folder) to create")
                             String seUrl,
                             @RestQuery("dest") @DefaultValue(defaultDestination)
                             @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                             String destination,
                             @RestQuery("storageAuth") @Parameter(required = false, description = "Credentials for the destination storage, Base-64 encoded 'user:password'")
                             String storageAuth) {

        LOG.infof("Create folder %s", seUrl);

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
                // Create folder
                return params.ts.createFolder(auth, storageAuth, seUrl);
            })
            .chain(created -> {
                // Folder got created
                LOG.infof("Created folder %s (%s)", seUrl, created);

                // Success
                return Uni.createFrom().item(Response.ok().build());
            })
            .onFailure().recoverWithItem(e -> {
                LOG.errorf("Failed to create folder %s", seUrl);
                return new ActionError(e, Arrays.asList(
                             Tuple2.of("seUrl", seUrl),
                             Tuple2.of("destination", destination)) ).toResponse();
            });

        return result;
    }

    /**
     * Delete existing folder.
     * @param auth The access token needed to call the service.
     * @param seUrl The link to the folder to delete.
     * @param destination The type of destination storage.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
     * @return API Response, wraps an ActionError entity in case of error
     */
    @DELETE
    @Path("/storage/folder")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "deleteFolder",  summary = "Delete existing folder from a storage system")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success"),
            @APIResponse(responseCode = "400", description="Invalid parameters/configuration or storage element is not a folder",
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
    public Uni<Response> deleteFolder(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                             @RestQuery("seUrl") @Parameter(required = true, description = "URL to the storage element (folder) to delete")
                             String seUrl,
                             @RestQuery("dest") @DefaultValue(defaultDestination)
                             @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                             String destination,
                             @RestQuery("storageAuth") @Parameter(required = false, description = "Credentials for the destination storage, Base-64 encoded 'user:password'")
                             String storageAuth) {

        LOG.infof("Delete folder %s", seUrl);

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
                // Delete folder
                return params.ts.deleteFolder(auth, storageAuth, seUrl);
            })
            .chain(deleted -> {
                // Folder got deleted
                LOG.infof("Deleted folder %s (%s)", seUrl, deleted);

                // Success
                return Uni.createFrom().item(Response.ok().build());
            })
            .onFailure().recoverWithItem(e -> {
                LOG.errorf("Failed to delete folder %s", seUrl);
                return new ActionError(e, Arrays.asList(
                             Tuple2.of("seUrl", seUrl),
                             Tuple2.of("destination", destination)) ).toResponse();
            });

        return result;
    }

    /**
     * Delete existing file.
     * @param auth The access token needed to call the service.
     * @param seUrl The link to the file to delete.
     * @param destination The type of destination storage.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
     * @return API Response, wraps an ActionError entity in case of error
     */
    @DELETE
    @Path("/storage/file")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "deleteFile",  summary = "Delete existing file from a storage system")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success"),
            @APIResponse(responseCode = "400", description="Invalid parameters/configuration or storage element is not a file",
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
    public Uni<Response> deleteFile(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                            @RestQuery("seUrl") @Parameter(required = true, description = "URL to the storage element (file) to delete")
                            String seUrl,
                            @RestQuery("dest") @DefaultValue(defaultDestination)
                            @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                            String destination,
                            @RestQuery("storageAuth") @Parameter(required = false, description = "Credentials for the destination storage, Base-64 encoded 'user:password'")
                            String storageAuth) {

        LOG.infof("Delete file %s", seUrl);

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
                // Delete file
                return params.ts.deleteFile(auth, storageAuth, seUrl);
            })
            .chain(deleted -> {
                // File got deleted
                LOG.infof("Deleted file %s (%s)", seUrl, deleted);

                // Success
                return Uni.createFrom().item(Response.ok().build());
            })
            .onFailure().recoverWithItem(e -> {
                LOG.errorf("Failed to delete file %s", seUrl);
                return new ActionError(e, Arrays.asList(
                             Tuple2.of("seUrl", seUrl),
                             Tuple2.of("destination", destination)) ).toResponse();
            });

        return result;
    }

    /**
     * Rename a file.
     * @param auth The access token needed to call the service.
     * @param operation The links to the old and new storage element URLs.
     * @param destination The type of destination storage.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
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
    public Uni<Response> renameFile(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                            StorageRenameOperation operation,
                            @RestQuery("dest") @DefaultValue(defaultDestination)
                            @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                            String destination,
                            @RestQuery("storageAuth") @Parameter(required = false, description = "Credentials for the destination storage, Base-64 encoded 'user:password'")
                            String storageAuth) {

        if(null != operation && null != operation.seUrlOld && null != operation.seUrlNew)
            LOG.infof("Renaming storage element %s to %s", operation.seUrlOld, operation.seUrlNew);
        else
            return Uni.createFrom().item(new ActionError("missingOperationParameters",
                                               Tuple2.of("destination", destination) )
                                                     .setStatus(Status.BAD_REQUEST)
                                                     .toResponse());

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
                // Rename storage element
                return params.ts.renameStorageElement(auth, storageAuth, operation.seUrlOld, operation.seUrlNew);
            })
            .chain(renamed -> {
                // Storage element got renamed
                LOG.infof("Renamed storage element %s to %s (%s)", operation.seUrlOld, operation.seUrlNew, renamed);

                // Success
                return Uni.createFrom().item(Response.ok().build());
            })
            .onFailure().recoverWithItem(e -> {
                LOG.errorf("Failed to rename storage element %s", operation.seUrlOld);
                return new ActionError(e, Arrays.asList(
                             Tuple2.of("seUrlOld", operation.seUrlOld),
                             Tuple2.of("seUrlNew", operation.seUrlNew),
                             Tuple2.of("destination", destination)) ).toResponse();
            });

        return result;
    }

    /**
     * Rename a folder.
     * @param auth The access token needed to call the service.
     * @param operation The links to the old and new storage element URLs.
     * @param destination The type of destination storage.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
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
    public Uni<Response> renameFolder(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                             StorageRenameOperation operation,
                             @RestQuery("dest") @DefaultValue(defaultDestination)
                             @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                             String destination,
                             @RestQuery("storageAuth") @Parameter(required = false, description = "Credentials for the destination storage, Base-64 encoded 'user:password'")
                             String storageAuth) {
        // This is the same for files and folders
        return renameFile(auth, operation, destination, storageAuth);
    }

}
