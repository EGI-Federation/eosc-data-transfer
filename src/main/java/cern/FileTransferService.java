package cern;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

import cern.model.*;


/***
 * REST client for File Transfer Service (that powers EGI Data Transfer)
 */
@RegisterProvider(value = FileTransferServiceExceptionMapper.class)
@Produces(MediaType.APPLICATION_JSON)
public interface FileTransferService {

    @POST
    @Path("/jobs")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<JobInfo> startTransferAsync(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, Job transfer);

    @GET
    @Path("/jobs")
    Uni<List<JobInfoExtended>> findTransfersAsync(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                                  @RestQuery("fields") String fields,
                                                  @RestQuery("limit") int limit,
                                                  @RestQuery("time_window")  String timeWindow,
                                                  @RestQuery("state_in")  String stateIn,
                                                  @RestQuery("source_se")  String srcStorageElement,
                                                  @RestQuery("dest_se")  String dstStorageElement,
                                                  @RestQuery("vo_name")  String voName,
                                                  @RestQuery("user_dn")  String userDN);

    @GET
    @Path("/jobs/{jobId}")
    Uni<JobInfoExtended> getTransferInfoAsync(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                              @RestPath("jobId") String jobId);

    @GET
    @Path("/jobs/{jobId}/files")
    Uni<List<JobFileInfo>> getTransferFilesAsync(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                                 @RestPath("jobId") String jobId);

    @GET
    @Path("/jobs/{jobId}/{fieldName}")
    Uni<Object> getTransferFieldAsync(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                      @RestPath("jobId") String jobId,
                                      @RestPath("fieldName") String fieldName);

    @DELETE
    @Path("/jobs/{jobId}")
    Uni<JobInfoExtended> cancelTransferAsync(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                             @RestPath("jobId") String jobId);
}
