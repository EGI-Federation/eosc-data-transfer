package egi.s3;

import egi.s3.model.ObjectInfo;
import egi.s3.model.ObjectOperation;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

import egi.fts.model.*;


/***
 * REST client for S3 storage systems
 */
@RegisterProvider(value = S3StorageExceptionMapper.class)
@Produces(MediaType.APPLICATION_JSON)
public interface S3StorageService {

    @GET
    @Path("/whoami")
    Uni<UserInfo> getUserInfoAsync(@RestHeader("Authorization") String auth);

    @GET
    @Path("/dm/list")
    Uni<Map<String, ObjectInfo>> listFolderContentAsync(@RestHeader("Authorization") String auth, @RestQuery("surl") String folderUrl);

    @GET
    @Path("/dm/stat")
    Uni<ObjectInfo> getObjectInfoAsync(@RestHeader("Authorization") String auth, @RestQuery("surl") String objectUrl);

    @POST
    @Path("/dm/mkdir")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<String> createFolderAsync(@RestHeader("Authorization") String auth, ObjectOperation folder);

    @POST
    @Path("/dm/rmdir")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<String> deleteFolderAsync(@RestHeader("Authorization") String auth, ObjectOperation folder);

    @POST
    @Path("/dm/rename")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<String> renameObjectAsync(@RestHeader("Authorization") String auth, ObjectOperation objects);

    @POST
    @Path("/dm/unlink")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<String> deleteFileAsync(@RestHeader("Authorization") String auth, ObjectOperation file);
}
