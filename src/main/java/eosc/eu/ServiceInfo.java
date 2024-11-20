package eosc.eu;

import eosc.eu.model.StorageContent;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;
import parser.ParserHelper;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Flow;


@Path("/")
public class ServiceInfo {

    private static final Logger log = Logger.getLogger(ServiceInfo.class);
    private static WebClient client;
    private Flow.Subscription subscription;

    @Inject
    MeterRegistry registry;

    @Inject
    ParsersConfig config;

    @Inject
    PortConfig port;


    /***
     * Construct with Vertx
     * @param vertx Injected Vertx instance
     */
    @Inject
    ServiceInfo(Vertx vertx) {
        this.client = WebClient.create(vertx);
    }


    /**
     * Parse Digital Object Identifier at specified URL and return list of files.
     * @param auth  Optional access token for accessing the data repository.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @GET
    @Path("/version")
    @SecurityRequirement(name = "OIDC")
    @Operation(operationId = "version",  summary = "Return service version information")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.TEXT_PLAIN,
                    schema = @Schema(implementation = String.class))),
    })
    public Uni<Response> getVersion(@RestHeader(HttpHeaders.AUTHORIZATION) String auth) {

        log.info("Querying version");

        var params = new ActionParameters();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Load module version from configuration
                final var config = ConfigProvider.getConfig();
                var version = config.getValue("quarkus.smallrye-openapi.info-version", String.class);

                // Got version information
                MDC.put("version", version);
                log.info("Got version");
                return Uni.createFrom().item(Response.ok(version).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Cannot load version");
                return new ActionError(e).toResponse();
            });

        return result;
    }
}
