package eosc.eu;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.tuples.Tuple2;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Flow;

import eosc.eu.model.*;
import parser.ParserHelper;


@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class DigitalObjectIdentifier {

    private static final Logger log = Logger.getLogger(DigitalObjectIdentifier.class);
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
    DigitalObjectIdentifier(Vertx vertx) {
        this.client = WebClient.create(vertx);
    }

    /**
     * Prepare a parser service that can parse the specified DOI.
     *
     * @param params Will receive the parser
     * @param auth The access token needed to call the parser
     * @param doi The DOI for a data set
     * @return true on success, updates field "parser"
     */
    private Uni<Boolean> getParser(String auth, ActionParameters params, String doi) {

        log.debug("Selecting DOI parser");

        if(null == doi || doi.isBlank()) {
            log.error("No DOI specified");
            return Uni.createFrom().item(false);
        }

        // Now try each parser until we find one that can parse the specified DOI
        ParserHelper helper = new ParserHelper(this.client);
        Uni<Boolean> result = Multi.createFrom().iterable(this.config.parsers().keySet())

            .onSubscription().invoke(sub -> {
                // Save the subscription, so we can cancel later
                this.subscription = sub;
            })
            .onItem().transformToUniAndConcatenate(parserId -> {
                // Instantiate parser
                var parserConfig = this.config.parsers().get(parserId);
                MDC.put("doiParser", parserConfig.name());
                log.debug("Trying next parser");

                ParserService parser = null;

                try {
                    // Get the class of the parser
                    var classType = Class.forName(parserConfig.className());

                    // Instantiate parser
                    parser = (ParserService)classType.getDeclaredConstructor(String.class).newInstance(parserId);
                }
                catch (ClassNotFoundException e) {
                    log.error(e.getMessage());
                }
                catch (NoSuchMethodException e) {
                    log.error(e.getMessage());
                }
                catch (InstantiationException e) {
                    log.error(e.getMessage());
                }
                catch (InvocationTargetException e) {
                    log.error(e.getMessage());
                }
                catch (IllegalAccessException e) {
                    log.error(e.getMessage());
                }
                catch (IllegalArgumentException e) {
                    log.error(e.getMessage());
                }

                return null != parser ?
                                Uni.createFrom().item(parser) :
                                Uni.createFrom().nullItem(); // Multi discards null items
            })
            .onItem().transformToUniAndConcatenate(parser -> {
                // Note: Remove this check once we can cancel the Multi stream
                // Check if this parser can handle the DOI
                if(null == params.parser)
                    return parser.canParseDOI(auth, doi, helper);

                // Once we selected a parser that supports the DOI, skip all others
                return Uni.createFrom().item(Tuple2.of(false, parser));
            })
            .onItem().transformToUniAndConcatenate(parserInfo -> {
                // Got info about a parser's support for our DOI
                var supported = parserInfo.getItem1();
                if(supported) {
                    // DOI supported
                    params.parser = parserInfo.getItem2();

                    // Initialize parser
                    var parserConfig = this.config.parsers().get(params.parser.getId());
                    var initOK = (null != parserConfig) ? params.parser.init(parserConfig, this.port) : false;

                    // Cancel iteration of parsers
//                    if(null != this.subscription) {
//                        var sub = this.subscription;
//                        this.subscription = null;
//                        sub.cancel();
//                    }

                    log.info("Found parser for DOI");
                    return Uni.createFrom().item(initOK);
                }

                MDC.remove("doiParser");
                return Uni.createFrom().item(false);
            })
            .onFailure().invoke(e -> {
                log.error("Failed to query configured parsers for support of DOI");
            })
            .collect()
            .in(BooleanAccumulator::new, (acc, supported) -> {
                acc.accumulateAny(supported);
            })
            .onItem().transform(BooleanAccumulator::get);

        return result;
    }

    /**
     * Parse Digital Object Identifier at specified URL and return list of files.
     * @param auth  Optional access token for accessing the data repository.
     * @param doi   The DOI to parse.
     * @param depth The level of recursion. If we have to call ourselves, this gets increased
     *              each time, providing for a mechanism to avoid infinite recursion.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @GET
    @Path("/parser")
    @SecurityRequirement(name = "OIDC")
    @Operation(operationId = "parse",  summary = "Extract source files from DOI")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = StorageContent.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Source not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class)))
    })
    public Uni<Response> parseDOI(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                  @Parameter(description = "The DOI to parse. Both canonical DOI notation "+
                                                           "and HTTP URLs are supported. ", required = true,
                                             example = "doi:10.5281/zenodo.6511035")
                                  @RestQuery String doi,
                                  @Parameter(hidden = true) @DefaultValue("1")
                                  @RestQuery int depth) {

        MDC.put("doi", doi);
        MDC.put("depth", depth);

        log.info("Parsing DOI");

        var params = new ActionParameters();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Pick parser service that recognizes this DOI
                return getParser(auth, params, doi);
            })
            .chain(canParse -> {
                if (!canParse) {
                    // Could not find suitable parser
                    log.error("No parser can handle DOI");
                    return Uni.createFrom().failure(new TransferServiceException("doiNotSupported"));
                }

                // Parse DOI and get source files
                return params.parser.parseDOI(auth, doi, depth);
            })
            .chain(sourceFiles -> {
                // Got list of source files, success
                MDC.put("fileCount", sourceFiles.count);
                log.info("Got source files");
                return Uni.createFrom().item(Response.ok(sourceFiles).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to parse DOI");
                return new ActionError(e, Tuple2.of("doi", doi)).toResponse();
            });

        return result;
    }
}
