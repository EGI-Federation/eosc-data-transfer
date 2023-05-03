package parser.esrf;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import parser.esrf.model.*;

import java.util.List;


/***
 * REST client for ESRF
 */
@RegisterProvider(value = EsrfExceptionMapper.class)
@Produces(MediaType.APPLICATION_JSON)
public interface Esrf {

    @POST
    @Path("/session")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<EsrfSession> getSessionAsync(EsrfCredentials credentials);

    @GET
    @Path("/doi/{authority}/{recordId}/datasets")
    Uni<List<EsrfDataSet>> getDataSetsAsync(@RestQuery("sessionId") String sessionId,
                                            @RestPath("authority") String authority,
                                            @RestPath("recordId") String recordId);

    @GET
    @Path("/catalogue/{sessionId}/dataset/id/{datasetId}/datafile")
    Uni<List<EsrfDataFile>> getDataFilesAsync(@RestPath("sessionId") String sessionId,
                                              @RestPath("datasetId") String datasetId);
}
