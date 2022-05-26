package eosc.eu;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.Arrays;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import eosc.eu.model.*;


/***
 * Class for data transfer operations and queries.
 * Dynamically selects the appropriate data transfer service, depending on the desired destination.
 */
@Path("/")
@SecuritySchemes(value = {
    @SecurityScheme(securitySchemeName = "none"),
    @SecurityScheme(securitySchemeName = "bearer",
            type = SecuritySchemeType.HTTP,
            scheme = "Bearer")} )
@Produces(MediaType.APPLICATION_JSON)
public class DataTransfer extends DataTransferBase {

    @Inject
    ServicesConfig config;

    private static final Logger LOG = Logger.getLogger(DataTransfer.class);


    /***
     * Constructor
     */
    public DataTransfer() {
        super(LOG);
    }

    /**
     * Initiate new transfer of multiple sets of files.
     * @param auth The access token needed to call the service.
     * @param transfer The details of the transfer (source and destination files, parameters).
     * @return API Response, wraps an ActionSuccess(TransferInfo) or an ActionError entity
     */
    @POST
    @Path("/transfers")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "startTransfer",  summary = "Initiate new transfer of multiple sets of files")
    @APIResponses(value = {
            @APIResponse(responseCode = "202", description = "Accepted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransferInfoExtended.class))),
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
                    // Start transfer
                    return params.ts.startTransfer(params.authorization, transfer);
                })
                .chain(transferInfo -> {
                    // Transfer started
                    LOG.infof("Started new transfer %s", transferInfo.jobId);

                    // Success
                    response.complete(Response.accepted(transferInfo).build());
                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.error("Failed to start new transfer");
                    if (!response.isDone())
                        response.complete(new ActionError(e,
                                                Tuple2.of("destination", config.destination())).toResponse());
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

    /***
     * Find transfers matching criteria.
     * @param auth The access token needed to call the service.
     * @param fields
     * @param limit
     * @param timeWindow
     * @param stateIn
     * @param srcStorageElement
     * @param dstStorageElement
     * @param delegationId
     * @param voName
     * @param userDN
     * @return API Response, wraps an ActionSuccess(TransferList) or an ActionError entity
     */
    @GET
    @Path("/transfers")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "findTransfers",  summary = "Find transfers matching search criteria")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransferList.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Not authorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Not authenticated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="No matching transfer",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response findTransfers(@RestHeader("Authorization") String auth,
                                  @RestQuery("fields") String fields,
                                  @RestQuery("limit") String limit,
                                  @RestQuery("time_window")  String timeWindow,
                                  @RestQuery("state_in")  String stateIn,
                                  @RestQuery("source_se")  String srcStorageElement,
                                  @RestQuery("dest_se")  String dstStorageElement,
                                  @RestQuery("dlg_id")  String delegationId,
                                  @RestQuery("vo_name")  String voName,
                                  @RestQuery("user_dn")  String userDN) {

        //LOG.infof("Retrieve details of transfer %s", jobId);

        /*
        try {
            ActionParameters ap = new ActionParameters(auth);
            CompletableFuture<Response> response = new CompletableFuture<>();
            Uni<ActionParameters> start = Uni.createFrom().item(ap);
            start
                    .chain(params -> {
                        // Pick transfer service and create REST client for it
                        if (!getTransferService(params)) {
                            // Could not get REST client
                            response.complete(new ActionError("invalidServiceConfig",
                                    Tuple2.of("destination", params.destination))
                                    .toResponse());
                            return Uni.createFrom().failure(new RuntimeException());
                        }

                        return Uni.createFrom().item(params);
                    })
                    .chain(params -> {
                        // Get transfer details
                        return params.ts.getTransferInfo(params.authorization, jobId);
                    })
                    .chain(transferInfo -> {
                        // Found transfer
                        LOG.infof("Transfer %s is %s", transferInfo.jobId, transferInfo.jobState);

                        // Success
                        response.complete(Response.ok(transferInfo).build());
                        return Uni.createFrom().nullItem();
                    })
                    .onFailure().invoke(e -> {
                        LOG.errorf("Failed to get details of transfer %s", jobId);
                        if (!response.isDone())
                            response.complete(new ActionError(e,
                                    Tuple2.of("jobId", jobId)).toResponse());
                    })
                    .subscribe().with(unused -> {});

            // Wait until transfer details retrieved (possibly with error)
            Response r = response.get();
            return r;
        } catch (InterruptedException e) {
            // Cancelled
            return new ActionError("getTransferInfoInterrupted").toResponse();
        } catch (ExecutionException e) {
            // Execution error
            return new ActionError("getTransferInfoExecutionError").toResponse();
        }

         */

        return null;
    }

    /**
     * Request information about a transfer.
     * @param auth The access token needed to call the service.
     * @param jobId The ID of the transfer to request info about.
     * @return API Response, wraps an ActionSuccess(TransferInfoExtended) or an ActionError entity
     */
    @GET
    @Path("/transfer/{jobId}")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "getTransferInfo",  summary = "Retrieve information about a transfer")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransferInfo.class))),
            @APIResponse(responseCode = "207", description="Transfer error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Not authorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Not authenticated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Transfer not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response getTransferInfo(@RestHeader("Authorization") String auth, String jobId) {

        LOG.infof("Retrieve details of transfer %s", jobId);

        try {
            ActionParameters ap = new ActionParameters(auth);
            CompletableFuture<Response> response = new CompletableFuture<>();
            Uni<ActionParameters> start = Uni.createFrom().item(ap);
            start
                .chain(params -> {
                    // Pick transfer service and create REST client for it
                    if (!getTransferService(params)) {
                        // Could not get REST client
                        response.complete(new ActionError("invalidServiceConfig",
                                              Tuple2.of("destination", params.destination))
                                                  .toResponse());
                        return Uni.createFrom().failure(new RuntimeException());
                    }

                    return Uni.createFrom().item(params);
                })
                .chain(params -> {
                    // Get transfer details
                    return params.ts.getTransferInfo(params.authorization, jobId);
                })
                .chain(transferInfo -> {
                    // Found transfer
                    LOG.infof("Transfer %s is %s", transferInfo.jobId, transferInfo.jobState);

                    // Success
                    response.complete(Response.ok(transferInfo).build());
                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.errorf("Failed to get details of transfer %s", jobId);
                    if (!response.isDone())
                        response.complete(new ActionError(e,
                                                Tuple2.of("jobId", jobId)).toResponse());
                })
                .subscribe().with(unused -> {});

            // Wait until transfer details retrieved (possibly with error)
            Response r = response.get();
            return r;
        } catch (InterruptedException e) {
            // Cancelled
            return new ActionError("getTransferInfoInterrupted").toResponse();
        } catch (ExecutionException e) {
            // Execution error
            return new ActionError("getTransferInfoExecutionError").toResponse();
        }
    }

    /**
     * Request specific field from information about a transfer.
     * @param auth The access token needed to call the service.
     * @param jobId The ID of the transfer to request info about.
     * @param fieldName The name of the TransferInfoExtended field to retrieve (except "kind").
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @GET
    @Path("/transfer/{jobId}/{fieldName}")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "getTransferInfoField",  summary = "Retrieve specific field from information about a transfer")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Object.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Not authorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Not authenticated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Transfer not found or field does not exist",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response getTransferInfoField(@RestHeader("Authorization") String auth, String jobId, String fieldName) {

        LOG.infof("Retrieve field '%s' from details of transfer %s", fieldName, jobId);

        try {
            ActionParameters ap = new ActionParameters(auth);
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
                    // Get transfer info field
                    return params.ts.getTransferInfoField(params.authorization, jobId, fieldName);
                })
                .chain(fieldValue -> {
                    // Found transfer and field
                    var entity = fieldValue.getEntity();
                    LOG.infof("Field %s of transfer %s is %s", fieldName, jobId, (null != entity) ? entity.toString() : "null");

                    // Success
                    response.complete(fieldValue);
                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.errorf("Failed to get field %s of transfer %s", fieldName, jobId);
                    if (!response.isDone())
                        response.complete(new ActionError(e, Arrays.asList(
                                                Tuple2.of("jobId", jobId),
                                                Tuple2.of("fieldName", fieldName)) ).toResponse());
                })
                .subscribe().with(unused -> {});

            // Wait until transfer info field is retrieved (possibly with error)
            Response r = response.get();
            return r;
        } catch (InterruptedException e) {
            // Cancelled
            return new ActionError("getTransferInfoFieldInterrupted").toResponse();
        } catch (ExecutionException e) {
            // Execution error
            return new ActionError("getTransferInfoFieldExecutionError").toResponse();
        }
    }

}
