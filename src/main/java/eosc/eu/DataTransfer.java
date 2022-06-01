package eosc.eu;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
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

import java.util.ArrayList;
import java.util.List;
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
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponses(value = {
            @APIResponse(responseCode = "202", description = "Accepted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransferInfo.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Not authorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response startTransfer(@RestHeader("Authorization") String auth, Transfer transfer,
                                  @RestQuery("dest") @DefaultValue(defaultDestination)
                                  @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                                  String destination) {

        LOG.info("Start new data transfer");

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
                    // Start transfer
                    return params.ts.startTransfer(auth, transfer);
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
                                                Tuple2.of("destination", destination)).toResponse());
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
     * @param fields Comma separated list of fields to return for each transfer
     * @param limit Maximum number of transfers to return
     * @param timeWindow For terminal states, limit results to 'hours[:minutes]' into the past
     * @param stateIn Comma separated list of job states to match, by default returns 'ACTIVE' only
     * @param srcStorageElement Source storage element
     * @param dstStorageElement Destination storage element
     * @param delegationId Filter by delegation ID of user who started the transfer
     * @param voName Filter by VO of user who started the transfer
     * @param userDN Filter by user who started the transfer
     * @return API Response, wraps an ActionSuccess(TransferList) or an ActionError entity
     */
    @GET
    @Path("/transfers")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "findTransfers",  summary = "Find transfers matching search criteria",
               description = "To prevent heavy queries, only non-terminal (active) jobs are returned.\n" +
                             "If the _state_in_ filter is used, make sure to also provide either _limit_ or _time_window_ to get completed jobs.")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransferList.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Not authorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="No matching transfer",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response findTransfers(@RestHeader("Authorization") String auth,
                                  @RestQuery("fields") @Parameter(description = "Comma separated list of fields to return for each transfer")
                                  String fields,
                                  @RestQuery("limit") @DefaultValue("100") @Parameter(description = "Maximum number of transfers to return")
                                  int limit,
                                  @RestQuery("time_window") @Parameter(description = "For terminal states, limit results to 'hours[:minutes]' into the past")
                                  String timeWindow,
                                  @RestQuery("state_in") @Parameter(description = "Comma separated list of job states to match, by default only finds active transfers")
                                  String stateIn,
                                  @RestQuery("source_se") @Parameter(description = "Source storage element")
                                  String srcStorageElement,
                                  @RestQuery("dest_se") @Parameter(description = "Destination storage element")
                                  String dstStorageElement,
                                  @RestQuery("dlg_id") @Parameter(description = "Filter by delegation ID of user who started the transfer")
                                  String delegationId,
                                  @RestQuery("vo_name") @Parameter(description = "Filter by virtual organization of user who started the transfer")
                                  String voName,
                                  @RestQuery("user_dn") @Parameter(description = "Filter by user who started the transfer")
                                  String userDN,
                                  @RestQuery("dest") @DefaultValue(defaultDestination)
                                  @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                                  String destination) {

        final String criteriaPrefix = "\n\t\t";
        String filters = String.format("%slimit = %s", criteriaPrefix, limit);

        if(null != fields && !fields.isEmpty())
            filters = String.format("%s%sfields = %s", filters, criteriaPrefix, fields);
        if(null != timeWindow && !timeWindow.isEmpty())
            filters = String.format("%s%stime_window = %s", filters, criteriaPrefix, timeWindow);
        if(null != stateIn && !stateIn.isEmpty())
            filters = String.format("%s%sstate_in = %s", filters, criteriaPrefix, stateIn);
        if(null != srcStorageElement && !srcStorageElement.isEmpty())
            filters = String.format("%s%ssource_se = %s", filters, criteriaPrefix, srcStorageElement);
        if(null != dstStorageElement && !dstStorageElement.isEmpty())
            filters = String.format("%s%sdest_se = %s", filters, criteriaPrefix, dstStorageElement);
        if(null != delegationId && !delegationId.isEmpty())
            filters = String.format("%s%sdlg_id = %s", filters, criteriaPrefix, delegationId);
        if(null != voName && !voName.isEmpty())
            filters = String.format("%s%svo_name = %s", filters, criteriaPrefix, voName);
        if(null != userDN && !userDN.isEmpty())
            filters = String.format("%s%suser_dn = %s", filters, criteriaPrefix, userDN);

        LOG.infof("Find data transfers matching criteria: %s", filters);

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
                                Tuple2.of("destination", params.destination)).toResponse());
                        return Uni.createFrom().failure(new RuntimeException());
                    }

                    return Uni.createFrom().item(params);
                })
                .chain(params -> {
                    // Find transfers
                    return params.ts.findTransfers(auth, fields, limit, timeWindow, stateIn,
                                                   srcStorageElement, dstStorageElement,
                                                   delegationId, voName, userDN);
                })
                .chain(matches -> {
                    // Found transfers
                    LOG.infof("Found %d matching transfers", matches.transfers.size());

                    // Success
                    response.complete(Response.ok(matches).build());
                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.error("Failed to find matching transfers");
                    if (!response.isDone()) {
                        List<Tuple2<String, String>> details = new ArrayList<>();
                        details.add(Tuple2.of("destination", destination));
                        details.add(Tuple2.of("limit", String.format("%d", limit)));
                        if(null != fields && !fields.isEmpty())
                            details.add(Tuple2.of("filter:fields", fields));
                        if(null != timeWindow && !timeWindow.isEmpty())
                            details.add(Tuple2.of("filter:time_window", timeWindow));
                        if(null != stateIn && !stateIn.isEmpty())
                            details.add(Tuple2.of("filter:state_in", stateIn));
                        if(null != srcStorageElement && !srcStorageElement.isEmpty())
                            details.add(Tuple2.of("filter:source_se", srcStorageElement));
                        if(null != dstStorageElement && !dstStorageElement.isEmpty())
                            details.add(Tuple2.of("filter:dest_se", dstStorageElement));
                        if(null != delegationId && !delegationId.isEmpty())
                            details.add(Tuple2.of("filter:dlg_id", delegationId));
                        if(null != voName && !voName.isEmpty())
                            details.add(Tuple2.of("filter:vo_name", voName));
                        if(null != userDN && !userDN.isEmpty())
                            details.add(Tuple2.of("filter:user_dn", userDN));

                        response.complete(new ActionError(e, details).toResponse());
                    }
                })
                .subscribe().with(unused -> {});

            // Wait until transfers are found (possibly with error)
            Response r = response.get();
            return r;
        } catch (InterruptedException e) {
            // Cancelled
            return new ActionError("findTransfersInterrupted").toResponse();
        } catch (ExecutionException e) {
            // Execution error
            return new ActionError("findTransfersExecutionError").toResponse();
        }
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
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransferInfoExtended.class))),
            @APIResponse(responseCode = "207", description="Transfer error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Not authorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Transfer not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response getTransferInfo(@RestHeader("Authorization") String auth, String jobId,
                                    @RestQuery("dest") @DefaultValue(defaultDestination)
                                    @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                                    String destination) {

        LOG.infof("Retrieve details of transfer %s", jobId);

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
                    // Get transfer details
                    return params.ts.getTransferInfo(auth, jobId);
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
                        response.complete(new ActionError(e, Arrays.asList(
                                                Tuple2.of("jobId", jobId),
                                                Tuple2.of("destination", destination)) ).toResponse());
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
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Transfer not found or field does not exist",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response getTransferInfoField(@RestHeader("Authorization") String auth, String jobId, String fieldName,
                                         @RestQuery("dest") @DefaultValue(defaultDestination)
                                         @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                                         String destination) {

        LOG.infof("Retrieve field '%s' from details of transfer %s", fieldName, jobId);

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
                    // Get transfer info field
                    return params.ts.getTransferInfoField(auth, jobId, fieldName);
                })
                .chain(fieldValue -> {
                    // Found transfer and field
                    var entity = fieldValue.getEntity();
                    LOG.infof("Field %s of transfer %s is %s", fieldName, jobId,
                                (null != entity) ? entity.toString() : "null");

                    // Success
                    response.complete(fieldValue);
                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.errorf("Failed to get field %s of transfer %s", fieldName, jobId);
                    if (!response.isDone())
                        response.complete(new ActionError(e, Arrays.asList(
                                                Tuple2.of("jobId", jobId),
                                                Tuple2.of("fieldName", fieldName),
                                                Tuple2.of("destination", destination)) ).toResponse());
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

    /**
     * Cancel a transfer.
     * @param auth The access token needed to call the service.
     * @param jobId The ID of the transfer to cancel.
     * @return API Response, wraps an ActionSuccess(TransferInfoExtended) or an ActionError entity
     */
    @DELETE
    @Path("/transfer/{jobId}")
    @SecurityRequirement(name = "bearer")
    @Operation(operationId = "cancelTransfer",  summary = "Cancel a transfer",
               description = "Returns the canceled transfer with its current status (canceled or any other final status).")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransferInfoExtended.class))),
            @APIResponse(responseCode = "207", description="Transfer error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Not authorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Transfer not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response cancelTransfer(@RestHeader("Authorization") String auth, String jobId,
                                   @RestQuery("dest") @DefaultValue(defaultDestination)
                                   @Parameter(schema = @Schema(implementation = Destination.class), description = "The destination storage")
                                   String destination) {

        LOG.infof("Cancel transfer %s", jobId);

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
                    // Cancel transfer
                    return params.ts.cancelTransfer(auth, jobId);
                })
                .chain(transferInfo -> {
                    // Canceled transfer
                    LOG.infof("Transfer %s is %s", transferInfo.jobId, transferInfo.jobState);

                    // Success
                    response.complete(Response.ok(transferInfo).build());
                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.errorf("Failed to cancel transfer %s", jobId);
                    if (!response.isDone())
                        response.complete(new ActionError(e, Arrays.asList(
                                                Tuple2.of("jobId", jobId),
                                                Tuple2.of("destination", destination)) ).toResponse());
                })
                .subscribe().with(unused -> {});

            // Wait until transfer details retrieved (possibly with error)
            Response r = response.get();
            return r;
        } catch (InterruptedException e) {
            // Cancelled
            return new ActionError("cancelTransferInterrupted").toResponse();
        } catch (ExecutionException e) {
            // Execution error
            return new ActionError("cancelTransferExecutionError").toResponse();
        }
    }

}
