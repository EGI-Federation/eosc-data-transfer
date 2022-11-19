package egi.fts;

import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

import egi.fts.model.*;


/***
 * REST client for File Transfer Service (that powers EGI Data Transfer)
 */
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
    @Path("/jobs/{jobId}/{fieldName}")
    Uni<Object> getTransferFieldAsync(@RestHeader("Authorization") String auth, String jobId, String fieldName);

    @DELETE
    @Path("/jobs/{jobId}")
    Uni<JobInfoExtended> cancelTransferAsync(@RestHeader("Authorization") String auth, String jobId);

    @GET
    @Path("/dm/list")
    Uni<Map<String, ObjectInfo>> listFolderContentAsync(@RestHeader("Authorization") String auth, @RestQuery("surl") String folderUrl);

    @GET
    @Path("/dm/stat")
    Uni<ObjectInfo> getObjectInfoAsync(@RestHeader("Authorization") String auth, @RestQuery("surl") String objectUrl);

    @POST
    @Path("/dm/mkdir")
    Uni<String> createFolderAsync(@RestHeader("Authorization") String auth, ObjectOperation folder);

    @POST
    @Path("/dm/rmdir")
    Uni<String> deleteFolderAsync(@RestHeader("Authorization") String auth, ObjectOperation folder);

    @POST
    @Path("/dm/rename")
    Uni<String> renameObjectAsync(@RestHeader("Authorization") String auth, ObjectOperation objects);

    @POST
    @Path("/dm/unlink")
    Uni<String> deleteFileAsync(@RestHeader("Authorization") String auth, ObjectOperation file);
}
