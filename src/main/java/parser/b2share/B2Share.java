package parser.b2share;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;

import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

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
