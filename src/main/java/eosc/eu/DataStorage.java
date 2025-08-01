package eosc.eu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
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

import java.util.Arrays;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import eosc.eu.model.*;
import eosc.eu.model.Transfer.Destination;


/***
 * Class for operations and queries about files and folders.
 * Dynamically selects the appropriate data transfer service, depending on the desired destination.
 */
@Path("/storage")
@Produces(MediaType.APPLICATION_JSON)
public class DataStorage extends DataTransferBase {

    private static final Logger log = Logger.getLogger(DataStorage.class);

    @Inject
    TransferConfig config;


    /***
     * Constructor
     */
    public DataStorage() {
        super(log);
    }

    /**
     * List all supported destinations, with info about each.
     * @return API Response, wraps an ActionSuccess(boolean) or an ActionError entity
     */
    @GET
    @Path("/destinations")
    @Operation(operationId = "listSupportedDestinations",  summary = "List all supported transfer destinations")
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = Destinations.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> listDestinations() {

        log.info("List supported destinations");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Iterate all configured destinations
                var destinations = new Destinations();
                for(var destination : this.config.destinations().keySet()) {
                    var destinationConfig = this.config.destinations().get(destination);
                    var description = destinationConfig.description().isPresent() ?
                                      destinationConfig.description().get() : "";

                    MDC.put("destination", destination);
                    var params = new ActionParameters(destination);
                    if (!getTransferService(params))
                        // Destination handled by unsupported transfer service
                        return Uni.createFrom().failure(new TransferServiceException("invalidServiceConfig"));

                    var ssID = destinationConfig.storageId().isEmpty() ? null : destinationConfig.storageId().get();
                    var storageConfig = null != ssID ? config.storages().get(ssID) : null;

                    // Check if a storage system is configured, create dummy REST client for it
                    getStorageSystem(params, "https://a.b.org/folder/file", "abc", "YTpi");

                    var destinationInfo = new DestinationInfo(destination,
                            null != storageConfig ? storageConfig.authType() : null,
                            null != storageConfig ? storageConfig.protocol() : null,
                            params.ts.getServiceName(),
                            null != params.ss ? params.ss.getServiceName() : null,
                            description);

                    destinations.add(destinationInfo);
                }

                MDC.remove("destination");
                return Uni.createFrom().item(destinations.toResponse());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list supported destinations");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Check if browsing destination storage is supported and what auth type it requires.
     * @param destination The destination storage type to get information about.
     * @return API Response, wraps an ActionSuccess(boolean) or an ActionError entity
     */
    @GET
    @Path("/destination")
    @Operation(operationId = "getDestinationInfo",  summary = "Retrieve information about transfer destination")
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = DestinationInfo.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> getDestinationInfo(@RestQuery("dest") @DefaultValue(DEFAULT_DESTINATION)
                                            @Parameter(schema = @Schema(implementation = Destination.class),
                                                       description = DESTINATION_STORAGE)
                                            String destination) {

        MDC.put("dest", destination);

        log.info("Retrieve information about a destination");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Pick transfer service and create REST client for it
                var params = new ActionParameters(destination);
                if (!getTransferService(params)) {
                    // Could not get REST client
                    return Uni.createFrom().failure(new TransferServiceException("invalidServiceConfig"));
                }

                // Check if a storage system is configured, but do not create REST client for it
                getStorageSystem(params, "https://a.b.org/folder/file", "abc", "YTpi");

                return Uni.createFrom().item(params);
            })
            .chain(params -> {
                // Retrieve the authentication type of the destination storage
                var destinationConfig = config.destinations().get(params.destination);
                if (null == destinationConfig)
                    // Unsupported destination
                    return Uni.createFrom().failure(new TransferServiceException("invalidDestination"));

                var ssID = destinationConfig.storageId().isEmpty() ? null : destinationConfig.storageId().get();
                var storageConfig = null != ssID ? config.storages().get(ssID) : null;

                // Check if browsing storage is supported
                var description = destinationConfig.description().isPresent() ?
                                  destinationConfig.description().get() : "";
                var destinationInfo = new DestinationInfo(destination,
                                                  null != storageConfig ? storageConfig.authType() : null,
                                                  null != storageConfig ? storageConfig.protocol() : null,
                                                  params.ts.getServiceName(),
                                                  null != params.ss ? params.ss.getServiceName() : null,
                                                  description);

                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    MDC.put("destInfo", objectMapper.writeValueAsString(destinationInfo));
                }
                catch (JsonProcessingException e) {}

                log.info("Got details of the destination");
                return Uni.createFrom().item(destinationInfo.toResponse());
            })
            .onFailure().recoverWithItem(e -> {
                log.errorf("Failed to retrieve info about destination %s", destination);
                return new ActionError(e, Tuple2.of("destination", destination)).toResponse();
            });

        return result;
    }

    /**
     * List the content of a folder.
     * @param auth The access token needed to call the service.
     * @param folderUrl The link to the folder to list content of.
     * @param destination The type of destination storage (selects transfer service to call).
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
     * @return API Response, wraps an ActionSuccess(StorageContent) or an ActionError entity
     */
    @GET
    @Path("/folder/list")
    @SecurityRequirement(name = "OIDC")
    @Operation(operationId = "listFolderContent",  summary = "List the content of a folder from a storage system")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = StorageContent.class))),
            @APIResponse(responseCode = "400",
                         description = "Invalid parameters/configuration or the storage element is not a folder",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Storage element not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> listFolderContent(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                           @RestQuery("folderUrl")
                                           @Parameter(required = true,
                                                      description =
                                                          "URL to the storage element (folder) to list content of")
                                           String folderUrl,
                                           @RestQuery("dest") @DefaultValue(DEFAULT_DESTINATION)
                                           @Parameter(schema = @Schema(implementation = Destination.class),
                                                      description = DESTINATION_STORAGE)
                                           String destination,
                                           @RestHeader(HEADER_STORAGE_AUTH)
                                           @Parameter(required = false,
                                                      description = STORAGE_AUTH)
                                           String storageAuth) {

        MDC.put("seUrl", folderUrl);
        MDC.put("dest", destination);

        log.info("List folder content");

        final String folderUrlWithAuth = applyStorageCredentials(destination, folderUrl, storageAuth);

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                if(null == folderUrlWithAuth)
                    return Uni.createFrom().failure(new TransferServiceException("urlInvalid"));

                // Pick storage system and create REST client for it
                var params = new ActionParameters(destination);
                if (!getStorageSystem(params, folderUrl, auth, storageAuth)) {
                    // Could not get REST client
                    return Uni.createFrom().failure(new TransferServiceException("invalidStorageConfig"));
                }

                if (null == params.ss) {
                    // Storage element manipulation not supported for this destination
                    return Uni.createFrom().failure(new TransferServiceException("browsingNotSupported"));
                }

                return Uni.createFrom().item(params);
            })
            .chain(params -> {
                // List folder content
                return params.ss.listFolderContent(auth, storageAuth, folderUrlWithAuth);
            })
            .chain(content -> {
                // Got folder content, success
                log.info("Got folder content");
                return Uni.createFrom().item(Response.ok(content).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list folder content");
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
     * @param destination The type of destination storage (selects transfer service to call).
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
     * @return API Response, wraps an ActionSuccess(StorageElement) or an ActionError entity
     */
    @GET
    @Path("/file")
    @SecurityRequirement(name = "OIDC")
    @Operation(operationId = "getFileInfo",  summary = "Retrieve information about a file in a storage system")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = StorageElement.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Storage element not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> getFileInfo(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                     @RestQuery("seUrl")
                                     @Parameter(required = true,
                                                description = "URL to the storage element (file) to get stats for")
                                     String seUrl,
                                     @RestQuery("dest") @DefaultValue(DEFAULT_DESTINATION)
                                     @Parameter(schema = @Schema(implementation = Destination.class),
                                                description = DESTINATION_STORAGE)
                                     String destination,
                                     @RestHeader(HEADER_STORAGE_AUTH)
                                     @Parameter(required = false, description = STORAGE_AUTH)
                                     String storageAuth) {

        MDC.put("seUrl", seUrl);
        MDC.put("dest", destination);

        log.info("Get details of storage element");

        final String seUrlWithAuth = applyStorageCredentials(destination, seUrl, storageAuth);

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                if(null == seUrlWithAuth)
                    return Uni.createFrom().failure(new TransferServiceException("urlInvalid"));

                // Pick storage system and create REST client for it
                var params = new ActionParameters(destination);
                if (!getStorageSystem(params, seUrl, auth, storageAuth)) {
                    // Could not get REST client
                    return Uni.createFrom().failure(new TransferServiceException("invalidStorageConfig"));
                }

                if (null == params.ss) {
                    // Storage element manipulation not supported for this destination
                    return Uni.createFrom().failure(new TransferServiceException("browsingNotSupported"));
                }

                return Uni.createFrom().item(params);
            })
            .chain(params -> {
                // Get storage element info
                return params.ss.getStorageElementInfo(auth, storageAuth, seUrlWithAuth);
            })
            .chain(seinfo -> {
                // Got storage element info, success
                log.info("Got storage element details");
                return Uni.createFrom().item(Response.ok(seinfo).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to get storage element details");
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
     * @param destination The type of destination storage (selects transfer service to call).
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
     * @return API Response, wraps an ActionSuccess(StorageElement) or an ActionError entity
     */
    @GET
    @Path("/folder")
    @SecurityRequirement(name = "OIDC")
    @Operation(operationId = "getFolderInfo",  summary = "Retrieve information about a folder in a storage system")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = StorageElement.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Storage element not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> getFolderInfo(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                       @RestQuery("seUrl")
                                       @Parameter(required = true,
                                                  description = "URL to the storage element (folder) to get stats for")
                                       String seUrl,
                                       @RestQuery("dest") @DefaultValue(DEFAULT_DESTINATION)
                                       @Parameter(schema = @Schema(implementation = Destination.class),
                                                  description = DESTINATION_STORAGE)
                                       String destination,
                                       @RestHeader(HEADER_STORAGE_AUTH)
                                       @Parameter(required = false, description = STORAGE_AUTH)
                                       String storageAuth) {
        // This is the same for files and folders
        return getFileInfo(auth, seUrl, destination, storageAuth);
    }

    /**
     * Create a new folder.
     * @param auth The access token needed to call the service.
     * @param seUrl The link to the folder to create.
     * @param destination The type of destination storage (selects transfer service to call).
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
     * @return API Response, wraps an ActionError entity in case of error
     */
    @POST
    @Path("/folder")
    @SecurityRequirement(name = "OIDC")
    @Operation(operationId = "createFolder",  summary = "Create new folder in a storage system")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success"),
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
    public Uni<Response> createFolder(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                      @RestQuery("seUrl")
                                      @Parameter(required = true,
                                                 description = "URL to the storage element (folder) to create")
                                      String seUrl,
                                      @RestQuery("dest") @DefaultValue(DEFAULT_DESTINATION)
                                      @Parameter(schema = @Schema(implementation = Destination.class),
                                                 description = DESTINATION_STORAGE)
                                      String destination,
                                      @RestHeader(HEADER_STORAGE_AUTH)
                                      @Parameter(required = false, description = STORAGE_AUTH)
                                      String storageAuth) {

        MDC.put("seUrl", seUrl);
        MDC.put("dest", destination);

        log.info("Creating folder");

        final String seUrlWithAuth = applyStorageCredentials(destination, seUrl, storageAuth);

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                if(null == seUrlWithAuth)
                    return Uni.createFrom().failure(new TransferServiceException("urlInvalid"));

                // Pick storage system and create REST client for it
                var params = new ActionParameters(destination);
                if (!getStorageSystem(params, seUrl, auth, storageAuth)) {
                    // Could not get REST client
                    return Uni.createFrom().failure(new TransferServiceException("invalidStorageConfig"));
                }

                if (null == params.ss) {
                    // Storage element manipulation not supported for this destination
                    return Uni.createFrom().failure(new TransferServiceException("browsingNotSupported"));
                }

                return Uni.createFrom().item(params);
            })
            .chain(params -> {
                // Create folder
                return params.ss.createFolder(auth, storageAuth, seUrlWithAuth);
            })
            .chain(created -> {
                // Folder got created, success
                log.info("Created folder");
                return Uni.createFrom().item(Response.ok().build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to create folder");
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
     * @param destination The type of destination storage (selects transfer service to call).
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
     * @return API Response, wraps an ActionError entity in case of error
     */
    @DELETE
    @Path("/folder")
    @SecurityRequirement(name = "OIDC")
    @Operation(operationId = "deleteFolder",  summary = "Delete existing folder from a storage system")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success"),
            @APIResponse(responseCode = "400",
                    description="Invalid parameters/configuration or storage element is not a folder",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Folder not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> deleteFolder(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                      @RestQuery("seUrl")
                                      @Parameter(required = true,
                                                 description = "URL to the storage element (folder) to delete")
                                      String seUrl,
                                      @RestQuery("dest") @DefaultValue(DEFAULT_DESTINATION)
                                      @Parameter(schema = @Schema(implementation = Destination.class),
                                                 description = DESTINATION_STORAGE)
                                      String destination,
                                      @RestHeader(HEADER_STORAGE_AUTH)
                                      @Parameter(required = false, description = STORAGE_AUTH)
                                      String storageAuth) {

        MDC.put("seUrl", seUrl);
        MDC.put("dest", destination);

        log.info("Deleting folder");

        final String seUrlWithAuth = applyStorageCredentials(destination, seUrl, storageAuth);

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                if(null == seUrlWithAuth)
                    return Uni.createFrom().failure(new TransferServiceException("urlInvalid"));

                // Pick storage system and create REST client for it
                var params = new ActionParameters(destination);
                if (!getStorageSystem(params, seUrl, auth, storageAuth)) {
                    // Could not get REST client
                    return Uni.createFrom().failure(new TransferServiceException("invalidStorageConfig"));
                }

                if (null == params.ss) {
                    // Storage element manipulation not supported for this destination
                    return Uni.createFrom().failure(new TransferServiceException("browsingNotSupported"));
                }

                return Uni.createFrom().item(params);
            })
            .chain(params -> {
                // Delete folder
                return params.ss.deleteFolder(auth, storageAuth, seUrlWithAuth);
            })
            .chain(deleted -> {
                // Folder got deleted, success
                log.info("Deleted folder");
                return Uni.createFrom().item(Response.ok().build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to delete folder");
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
     * @param destination The type of destination storage (selects transfer service to call).
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
     * @return API Response, wraps an ActionError entity in case of error
     */
    @DELETE
    @Path("/file")
    @SecurityRequirement(name = "OIDC")
    @Operation(operationId = "deleteFile",  summary = "Delete existing file from a storage system")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success"),
            @APIResponse(responseCode = "400",
                    description="Invalid parameters/configuration or storage element is not a file",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="File not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> deleteFile(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                    @RestQuery("seUrl")
                                    @Parameter(required = true,
                                               description = "URL to the storage element (file) to delete")
                                    String seUrl,
                                    @RestQuery("dest") @DefaultValue(DEFAULT_DESTINATION)
                                    @Parameter(schema = @Schema(implementation = Destination.class),
                                               description = DESTINATION_STORAGE)
                                    String destination,
                                    @RestHeader(HEADER_STORAGE_AUTH)
                                    @Parameter(required = false, description = STORAGE_AUTH)
                                    String storageAuth) {

        MDC.put("seUrl", seUrl);
        MDC.put("dest", destination);

        log.info("Deleting file");

        final String seUrlWithAuth = applyStorageCredentials(destination, seUrl, storageAuth);

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                if(null == seUrlWithAuth)
                    return Uni.createFrom().failure(new TransferServiceException("urlInvalid"));

                // Pick storage system and create REST client for it
                var params = new ActionParameters(destination);
                if (!getStorageSystem(params, seUrl, auth, storageAuth)) {
                    // Could not get REST client
                    return Uni.createFrom().failure(new TransferServiceException("invalidStorageConfig"));
                }

                if (null == params.ss) {
                    // Storage element manipulation not supported for this destination
                    return Uni.createFrom().failure(new TransferServiceException("browsingNotSupported"));
                }

                return Uni.createFrom().item(params);
            })
            .chain(params -> {
                // Delete file
                return params.ss.deleteFile(auth, storageAuth, seUrlWithAuth);
            })
            .chain(deleted -> {
                // File got deleted, success
                log.infof("Deleted file");
                return Uni.createFrom().item(Response.ok().build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to delete file");
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
     * @param destination The type of destination storage (selects transfer service to call).
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
     * @return API Response, wraps an ActionError entity in case of error
     */
    @PUT
    @Path("/file")
    @SecurityRequirement(name = "OIDC")
    @Operation(operationId = "renameFile",  summary = "Rename existing file in a storage system")
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success"),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="File not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> renameFile(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                    StorageRenameOperation operation,
                                    @RestQuery("dest") @DefaultValue(DEFAULT_DESTINATION)
                                    @Parameter(schema = @Schema(implementation = Destination.class),
                                               description = DESTINATION_STORAGE)
                                    String destination,
                                    @RestHeader(HEADER_STORAGE_AUTH)
                                    @Parameter(required = false, description = STORAGE_AUTH)
                                    String storageAuth) {

        MDC.put("dest", destination);

        if(null != operation && null != operation.seUrlOld && null != operation.seUrlNew) {
            MDC.put("seUrlOld", operation.seUrlOld);
            MDC.put("seUrlNew", operation.seUrlNew);
            log.info("Renaming storage element");
        }
        else {
            log.error("Cannot rename storage element");
            return Uni.createFrom().item(new ActionError("missingOperationParameters",
                                         Arrays.asList(Tuple2.of("seUrlOld", operation.seUrlOld),
                                                       Tuple2.of("seUrlNew", operation.seUrlNew),
                                                       Tuple2.of("destination", destination)) )
                                            .setStatus(Status.BAD_REQUEST)
                                            .toResponse());
        }

        final String seUrlOldWithAuth = applyStorageCredentials(destination, operation.seUrlOld, storageAuth);
        final String seUrlNewWithAuth = applyStorageCredentials(destination, operation.seUrlNew, storageAuth);

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                if(null == seUrlOldWithAuth || null == seUrlNewWithAuth)
                    return Uni.createFrom().failure(new TransferServiceException("urlInvalid"));

                // Pick storage system and create REST client for it
                var params = new ActionParameters(destination);
                if (!getStorageSystem(params, operation.seUrlOld, auth, storageAuth)) {
                    // Could not get REST client
                    return Uni.createFrom().failure(new TransferServiceException("invalidStorageConfig"));
                }

                if (null == params.ss) {
                    // Storage element manipulation not supported for this destination
                    return Uni.createFrom().failure(new TransferServiceException("browsingNotSupported"));
                }

                return Uni.createFrom().item(params);
            })
            .chain(params -> {
                // Rename storage element
                return params.ss.renameStorageElement(auth, storageAuth, seUrlOldWithAuth, seUrlNewWithAuth);
            })
            .chain(renamed -> {
                // Storage element got renamed, success
                log.info("Renamed storage element");
                return Uni.createFrom().item(Response.ok().build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to rename storage element");
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
     * @param destination The type of destination storage (selects transfer service to call).
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
     * @return API Response, wraps an ActionError entity in case of error
     */
    @PUT
    @Path("/folder")
    @SecurityRequirement(name = "OIDC")
    @Operation(operationId = "renameFolder",  summary = "Rename existing folder in a storage system")
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success"),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Folder not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> renameFolder(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                      StorageRenameOperation operation,
                                      @RestQuery("dest") @DefaultValue(DEFAULT_DESTINATION)
                                      @Parameter(schema = @Schema(implementation = Destination.class),
                                              description = DESTINATION_STORAGE)
                                      String destination,
                                      @RestHeader(HEADER_STORAGE_AUTH)
                                      @Parameter(required = false, description = STORAGE_AUTH)
                                      String storageAuth) {
        // This is the same for files and folders
        return renameFile(auth, operation, destination, storageAuth);
    }
}
