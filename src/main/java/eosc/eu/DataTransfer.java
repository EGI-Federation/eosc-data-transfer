package eosc.eu;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.micrometer.core.instrument.MeterRegistry;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import eosc.eu.model.*;
import eosc.eu.model.Transfer.Destination;


/***
 * Class for data transfer operations and queries.
 * Dynamically selects the appropriate data transfer service, depending on the desired destination.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class DataTransfer extends DataTransferBase {

    private static final Logger log = Logger.getLogger(DataTransfer.class);

    @Inject
    MeterRegistry registry;


    /***
     * Constructor
     */
    public DataTransfer() { super(log); }

    /**
     * Initiate new transfer of multiple sets of files.
     * @param auth The access token needed to call the service.
     * @param transfer The details of the transfer (source and destination files, parameters).
     * @param destination The type of destination storage (selects transfer service to call).
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value".
     * @return API Response, wraps an ActionSuccess(TransferInfo) or an ActionError entity
     */
    @POST
    @Path("/transfers")
    @SecurityRequirement(name = "OIDC")
    @Operation(operationId = "startTransfer",  summary = "Initiate new transfer of multiple sets of files")
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponses(value = {
            @APIResponse(responseCode = "202", description = "Accepted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = TransferInfo.class))),
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
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> startTransfer(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, Transfer transfer,
                                       @RestQuery("dest") @DefaultValue(DEFAULT_DESTINATION)
                                       @Parameter(schema = @Schema(implementation = Destination.class),
                                                  description = DESTINATION_STORAGE)
                                       String destination,
                                       @RestHeader(HEADER_STORAGE_AUTH)
                                       @Parameter(required = false, description = STORAGE_AUTH)
                                       String storageAuth) {

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            MDC.put("transfer", objectMapper.writeValueAsString(transfer));
        }
        catch (JsonProcessingException e) {
            var ae = new ActionError(e, Tuple2.of("destination", destination));
            return Uni.createFrom().item(ae.setStatus(Response.Status.BAD_REQUEST).toResponse());
        }

        MDC.put("dest", destination);

        log.info("Starting new data transfer");

        // If authentication info is provided for the storage, embed it in every FTP destination URL
        if(null != storageAuth && !storageAuth.isBlank() && destination.equalsIgnoreCase(Destination.ftp.toString())) {
            // If the destination is FTP, embed storage credentials in all
            // destination URLs (will not check each URL if the protocol is "ftp")
            for(var payload : transfer.files) {
                List<String> fixedDestinations = new ArrayList<>();
                for(var seUrl : payload.destinations) {
                    var seUrlFixed = applyStorageCredentials(destination, seUrl, storageAuth);
                    if(null == seUrlFixed) {
                        // Could not add credentials to invalid URL
                        log.error("Failed to start new transfer");
                        return Uni.createFrom().item(new ActionError("urlInvalid", Arrays.asList(
                                                                         Tuple2.of("url", seUrl),
                                                                         Tuple2.of("destination", destination) ))
                                                                .toResponse());
                    }

                    fixedDestinations.add(seUrlFixed);
                }

                payload.destinations = fixedDestinations;
            }
        }

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
                // Start transfer
                return params.ts.startTransfer(auth, storageAuth, transfer);
            })
            .chain(transferInfo -> {
                // Transfer started, success
                log.info("Started new transfer");
                return Uni.createFrom().item(Response.accepted(transferInfo).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to start new transfer");
                return new ActionError(e, Tuple2.of("destination", destination)).toResponse();
            });

        return result;
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
     * @param destination The type of destination storage (selects transfer service to call).
     * @return API Response, wraps an ActionSuccess(TransferList) or an ActionError entity
     */
    @GET
    @Path("/transfers")
    @SecurityRequirement(name = "OIDC")
    @Operation(operationId = "findTransfers",  summary = "Find transfers matching search criteria",
               description = "To prevent heavy queries, only non-terminal (active) jobs are returned.\n" +
                             "If the _state_in_ filter is used, make sure to also provide either _limit_ " +
                             "or _time_window_ to get completed jobs.")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = TransferList.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="No matching transfer",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> findTransfers(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                       @RestQuery("fields")
                                       @Parameter(description =
                                               "Comma separated list of fields to return for each transfer")
                                       String fields,
                                       @RestQuery("limit") @DefaultValue("100")
                                       @Parameter(description = "Maximum number of transfers to return")
                                       int limit,
                                       @RestQuery("time_window")
                                       @Parameter(description =
                                               "For terminal states, limit results to 'hours[:minutes]' into the past")
                                       String timeWindow,
                                       @RestQuery("state_in")
                                       @Parameter(description =
                                               "Comma separated list of job states to match, " +
                                               "by default only finds active transfers")
                                       String stateIn,
                                       @RestQuery("source_se")
                                       @Parameter(description = "Source storage element")
                                       String srcStorageElement,
                                       @RestQuery("dest_se")
                                       @Parameter(description = "Destination storage element")
                                       String dstStorageElement,
                                       @RestQuery("dlg_id")
                                       @Parameter(description =
                                               "Filter by delegation ID of user who started the transfer")
                                       String delegationId,
                                       @RestQuery("vo_name")
                                       @Parameter(description =
                                               "Filter by virtual organization of user who started the transfer")
                                       String voName,
                                       @RestQuery("user_dn")
                                       @Parameter(description = "Filter by user who started the transfer")
                                       String userDN,
                                       @RestQuery("dest") @DefaultValue(DEFAULT_DESTINATION)
                                       @Parameter(schema = @Schema(implementation = Destination.class),
                                                  description = DESTINATION_STORAGE)
                                       String destination) {

        MDC.put("dest", destination);
        MDC.put("limit", limit);

        if(null != fields && !fields.isEmpty())
            MDC.put("fields", fields);
        if(null != timeWindow && !timeWindow.isEmpty())
            MDC.put("filter.time_window", timeWindow);
        if(null != stateIn && !stateIn.isEmpty())
            MDC.put("filter.state_in", stateIn);
        if(null != srcStorageElement && !srcStorageElement.isEmpty())
            MDC.put("filter.source_se", srcStorageElement);
        if(null != dstStorageElement && !dstStorageElement.isEmpty())
            MDC.put("filter.dest_se", dstStorageElement);
        if(null != delegationId && !delegationId.isEmpty())
            MDC.put("filter.dlg_id", delegationId);
        if(null != voName && !voName.isEmpty())
            MDC.put("filter.vo_name", voName);
        if(null != userDN && !userDN.isEmpty())
            MDC.put("filter.user_dn", userDN);

        log.info("Finding data transfers matching criteria");

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
                // Find transfers
                return params.ts.findTransfers(auth, fields, limit, timeWindow, stateIn,
                                               srcStorageElement, dstStorageElement,
                                               delegationId, voName, userDN);
            })
            .chain(matches -> {
                // Found transfers, success
                log.info("Found matching transfers");
                return Uni.createFrom().item(Response.ok(matches).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to find matching transfers");

                List<Tuple2<String, String>> details = new ArrayList<>();
                details.add(Tuple2.of("destination", destination));
                details.add(Tuple2.of("limit", String.format("%d", limit)));
                if(null != fields && !fields.isEmpty())
                    details.add(Tuple2.of("fields", fields));
                if(null != timeWindow && !timeWindow.isEmpty())
                    details.add(Tuple2.of("filter.time_window", timeWindow));
                if(null != stateIn && !stateIn.isEmpty())
                    details.add(Tuple2.of("filter.state_in", stateIn));
                if(null != srcStorageElement && !srcStorageElement.isEmpty())
                    details.add(Tuple2.of("filter.source_se", srcStorageElement));
                if(null != dstStorageElement && !dstStorageElement.isEmpty())
                    details.add(Tuple2.of("filter.dest_se", dstStorageElement));
                if(null != delegationId && !delegationId.isEmpty())
                    details.add(Tuple2.of("filter.dlg_id", delegationId));
                if(null != voName && !voName.isEmpty())
                    details.add(Tuple2.of("filter.vo_name", voName));
                if(null != userDN && !userDN.isEmpty())
                    details.add(Tuple2.of("filter.user_dn", userDN));

                return new ActionError(e, details).toResponse();
            });

        return result;
    }

    /**
     * Request information about a transfer.
     * @param auth The access token needed to call the service.
     * @param jobId The ID of the transfer to request info about.
     * @param destination The type of destination storage (selects transfer service to call).
     * @return API Response, wraps an ActionSuccess(TransferInfoExtended) or an ActionError entity
     */
    @GET
    @Path("/transfer/{jobId}")
    @SecurityRequirement(name = "OIDC")
    @Operation(operationId = "getTransferInfo",  summary = "Retrieve information about a transfer")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = TransferInfoExtended.class))),
            @APIResponse(responseCode = "207", description="Transfer error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Transfer not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> getTransferInfo(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, String jobId,
                                         @RestQuery("dest") @DefaultValue(DEFAULT_DESTINATION)
                                         @Parameter(schema = @Schema(implementation = Destination.class),
                                                    description = DESTINATION_STORAGE)
                                         String destination) {

        MDC.put("jobId", jobId);
        MDC.put("dest", destination);

        log.info("Retrieving details of transfer");

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
                // Get transfer details
                return params.ts.getTransferInfo(auth, jobId);
            })
            .chain(transferInfo -> {
                // Got transfer details, success
                MDC.put("jobState", transferInfo.jobState);
                log.infof("Transfer is %s", transferInfo.jobState);
                return Uni.createFrom().item(Response.ok(transferInfo).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to get transfer details");
                return new ActionError(e, Arrays.asList(
                             Tuple2.of("jobId", jobId),
                             Tuple2.of("destination", destination)) ).toResponse();
            });

        return result;
    }

    /**
     * Request specific field from information about a transfer.
     * @param auth The access token needed to call the service.
     * @param jobId The ID of the transfer to request info about.
     * @param fieldName The name of the TransferInfoExtended field to retrieve (except "kind").
     * @param destination The type of destination storage (selects transfer service to call).
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @GET
    @Path("/transfer/{jobId}/{fieldName}")
    @SecurityRequirement(name = "OIDC")
    @Operation(operationId = "getTransferInfoField",
               summary = "Retrieve specific field from information about a transfer")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = Object.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Transfer not found or field does not exist",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> getTransferInfoField(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                              String jobId, String fieldName,
                                              @RestQuery("dest") @DefaultValue(DEFAULT_DESTINATION)
                                              @Parameter(schema = @Schema(implementation = Destination.class),
                                                         description = DESTINATION_STORAGE)
                                              String destination) {

        MDC.put("jobId", jobId);
        MDC.put("fieldName", fieldName);
        MDC.put("dest", destination);

        log.info("Retrieving field from transfer details");

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
                // Get transfer info field
                return params.ts.getTransferInfoField(auth, jobId, fieldName);
            })
            .chain(fieldValue -> {
                // Found transfer and field, success
                log.info("Got field of transfer");
                return Uni.createFrom().item(Response.ok(fieldValue).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to get field of transfer");
                return new ActionError(e, Arrays.asList(
                             Tuple2.of("jobId", jobId),
                             Tuple2.of("fieldName", fieldName),
                             Tuple2.of("destination", destination)) ).toResponse();
            });

        return result;
    }

    /**
     * Cancel a transfer.
     * @param auth The access token needed to call the service.
     * @param jobId The ID of the transfer to cancel.
     * @param destination The type of destination storage (selects transfer service to call).
     * @return API Response, wraps an ActionSuccess(TransferInfoExtended) or an ActionError entity
     */
    @DELETE
    @Path("/transfer/{jobId}")
    @SecurityRequirement(name = "OIDC")
    @Operation(operationId = "cancelTransfer",  summary = "Cancel a transfer",
               description = "Returns the canceled transfer with its current status " +
                             "(canceled or any other final status).")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = TransferInfoExtended.class))),
            @APIResponse(responseCode = "207", description="Transfer error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "403", description="Permission denied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Transfer not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "419", description="Re-delegate credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> cancelTransfer(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, String jobId,
                                        @RestQuery("dest") @DefaultValue(DEFAULT_DESTINATION)
                                        @Parameter(schema = @Schema(implementation = Destination.class),
                                                   description = DESTINATION_STORAGE)
                                        String destination) {

        MDC.put("jobId", jobId);
        MDC.put("dest", destination);

        log.info("Canceling transfer");

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
                // Cancel transfer
                return params.ts.cancelTransfer(auth, jobId);
            })
            .chain(transferInfo -> {
                // Canceled transfer, success
                MDC.put("jobState", transferInfo.jobState);
                log.infof("Transfer is %s", transferInfo.jobState);
                return Uni.createFrom().item(Response.ok(transferInfo).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to cancel transfer");
                return new ActionError(e, Arrays.asList(
                        Tuple2.of("jobId", jobId),
                        Tuple2.of("destination", destination)) ).toResponse();
            });

        return result;
    }

}
