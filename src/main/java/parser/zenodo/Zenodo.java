package parser.zenodo;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;

import javax.ws.rs.PathParam;
import javax.ws.rs.Path;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import parser.zenodo.model.*;


/***
 * REST client for Zenodo
 */
@Path("/api")
@RegisterProvider(value = ZenodoExceptionMapper.class)
public interface Zenodo {

    @GET
    @Path("/records/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<ZenodoRecord> getRecordAsync(@PathParam("id") String recordId);
}
