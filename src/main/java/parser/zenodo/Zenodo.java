package parser.zenodo;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;

import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;

import parser.zenodo.model.*;


/***
 * REST client for Zenodo
 */
@Path("/api")
@RegisterProvider(value = ZenodoExceptionMapper.class)
public interface Zenodo {

    @GET
    @Path("/records/{id}")
    @Produces("application/vnd.zenodo.v1+json")
    Uni<ZenodoRecord> getRecordAsync(@PathParam("id") String recordId);
}
