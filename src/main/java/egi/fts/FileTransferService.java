package egi.fts;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

import egi.fts.model.*;


/***
 * REST client for File Transfer Service (that powers EGI Data Transfer)
 */
@RegisterProvider(value = FileTransferServiceExceptionMapper.class)
@Produces(MediaType.APPLICATION_JSON)
public interface FileTransferService {

    @GET
    @Path("/whoami")
    Uni<UserInfo> getUserInfoAsync(@RestHeader("Authorization") String auth);

    @POST
    @Path("/jobs")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<JobInfo> startTransferAsync(@RestHeader("Authorization") String auth, Job transfer);

    @GET
    @Path("/jobs")
    Uni<List<JobInfoExtended>> findTransfersAsync(@RestHeader("Authorization") String auth,
                                                  @RestQuery("fields") String fields,
                                                  @RestQuery("limit") int limit,
                                                  @RestQuery("time_window")  String timeWindow,
                                                  @RestQuery("state_in")  String stateIn,
                                                  @RestQuery("source_se")  String srcStorageElement,
                                                  @RestQuery("dest_se")  String dstStorageElement,
                                                  @RestQuery("dlg_id")  String delegationId,
                                                  @RestQuery("vo_name")  String voName,
                                                  @RestQuery("user_dn")  String userDN);

    @GET
    @Path("/jobs/{jobId}")
    Uni<JobInfoExtended> getTransferInfoAsync(@RestHeader("Authorization") String auth, String jobId);

    @GET
    @Path("/jobs/{jobId}/files")
    Uni<List<JobFileInfo>> getTransferFilesAsync(@RestHeader("Authorization") String auth, String jobId);

    @GET
    @Path("/jobs/{jobId}/{fieldName}")
    Uni<Object> getTransferFieldAsync(@RestHeader("Authorization") String auth, String jobId, String fieldName);

    @DELETE
    @Path("/jobs/{jobId}")
    Uni<JobInfoExtended> cancelTransferAsync(@RestHeader("Authorization") String auth, String jobId);

    // NOTE: The methods below should return S3Info, but auto deserialization fails for return type Uni<S3Info>
    // However, manually deserializing the returned JSON string using ObjectMapper works fine!

    @POST
    @Path("/config/cloud_storage")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<String> registerS3HostAsync(@RestHeader("Authorization") String auth, S3Info s3Info);

    @POST
    @Path("/config/cloud_storage/{s3Host}")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<String> configureS3HostAsync(@RestHeader("Authorization") String auth, String s3Host, S3Info s3Info);
}
