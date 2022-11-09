package parser.b2share;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;

import javax.ws.rs.PathParam;
import javax.ws.rs.Path;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import parser.b2share.model.*;


/***
 * REST client for B2Share
 */
@Path("/api")
@RegisterProvider(value = B2ShareExceptionMapper.class)
public interface B2Share {

    @GET
    @Path("/records/{recordId}")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<B2ShareRecord> getRecordAsync(@PathParam("recordId") String recordId);

    @GET
    @Path("/files/{bucketId}")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<B2ShareBucket> getFilesInBucketAsync(@PathParam("bucketId") String bucketId);
}
