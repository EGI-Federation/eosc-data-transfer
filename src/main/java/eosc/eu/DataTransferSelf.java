package eosc.eu;

import io.smallrye.mutiny.Uni;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import eosc.eu.model.StorageContent;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;


/***
 * REST client to invoke ourselves (recursively)
 */
public interface DataTransferSelf {

    @GET
    @Path("/parser")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<StorageContent> parseDOIAsync(@RestHeader("Authorization") String auth,
                                      @RestQuery("doi") String doi,
                                      @RestQuery("level") int level);
}
