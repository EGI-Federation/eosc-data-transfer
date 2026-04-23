package grnet;

import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import io.smallrye.mutiny.Uni;
import io.quarkus.oidc.client.filter.OidcClientFilter;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.HttpHeaders;

import grnet.model.*;


/***
 * REST client for the Accounting Service
 */
@RegisterProvider(AccountingServiceExceptionMapper.class)
@OidcClientFilter
@Produces(MediaType.APPLICATION_JSON)
public interface AccountingService {

    @POST
    @Path("/accounting-system/installations/external/metrics")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<DataTransferUsageRecord> sendUsageRecord(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                                 @RestQuery("externalId") String installationId,
                                                 DataTransferUsageRecord usageRecord);
}
