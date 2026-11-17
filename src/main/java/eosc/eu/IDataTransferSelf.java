package eosc.eu;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.HttpHeaders;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import eosc.eu.model.StorageContent;


/***
 * REST client to invoke ourselves (recursively)
 */
@Produces(MediaType.APPLICATION_JSON)
public interface DataTransferSelf {

    @GET
    @Path("/parser")
    Uni<StorageContent> parseDOIAsync(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                      @RestQuery("doi") String doi,
                                      @RestQuery("level") int level);
}
