package eosc.eu;

import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.reactive.RestQuery;
import io.quarkus.oidc.token.propagation.common.AccessToken;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import eosc.eu.model.StorageContent;
import eosc.eu.model.TransferInfoExtended;
import eosc.eu.model.TransferPayloadInfo.FileDetails;


/***
 * REST client to invoke ourselves (recursively)
 */
public interface DataTransferSelf {

    @GET
    @Path("/parser")
    @Produces(MediaType.APPLICATION_JSON)
    @AccessToken
    Uni<StorageContent> parseDOIAsync(@RestQuery("doi") String doi, @RestQuery("level") int level);

    @GET
    @Path("/transfer/{jobId}")
    Uni<TransferInfoExtended> getTransferInfo(String jobId,
                                              @RestQuery("dest") String destination,
                                              @RestQuery("fileInfo") FileDetails fileInfo);
}
