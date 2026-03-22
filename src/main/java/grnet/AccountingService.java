package grnet;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.jboss.resteasy.reactive.RestQuery;
import io.quarkus.oidc.token.propagation.common.AccessToken;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import grnet.model.*;


/***
 * REST client for the Accounting Service
 */
@RegisterProvider(value = AccountingServiceExceptionMapper.class)
@AccessToken
@Produces(MediaType.APPLICATION_JSON)
public interface AccountingService {

    @POST
    @Path("/accounting-system/installations/external/metrics")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<DataTransferUsageRecord> sendUsageRecord(@RestQuery("externalId") String installationId,
                                                 DataTransferUsageRecord usageRecord);
}
