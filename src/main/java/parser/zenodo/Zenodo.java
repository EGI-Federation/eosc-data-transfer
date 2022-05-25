package parser.zenodo;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import parser.zenodo.model.*;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;

import javax.ws.rs.QueryParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.Path;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


/***
 * REST client for Zenodo
 */
@Path("/api")
@RegisterProvider(value = ZenodoExceptionMapper.class)
public interface Zenodo {

    @GET
    @Path("/records/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<ZenodoRecord> getRecordsAsync(@PathParam("id") String recordId);
}
