package egi.fts;

import egi.fts.model.*;

import io.smallrye.mutiny.Uni;
import io.vertx.core.cli.annotations.Description;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;

import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;


/***
 * REST client for File Transfer Service (that powers EGI Data Transfer)
 */
@Produces(MediaType.APPLICATION_JSON)
@RegisterProvider(value = FileTransferServiceExceptionMapper.class)
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
}
