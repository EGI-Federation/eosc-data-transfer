package eosc.eu;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import eosc.eu.model.*;
import parser.zenodo.Zenodo;


@Path("/")
@SecuritySchemes(value = {
    @SecurityScheme(securitySchemeName = "none"),
    @SecurityScheme(securitySchemeName = "bearer",
            type = SecuritySchemeType.HTTP,
            scheme = "Bearer")} )
@Produces(MediaType.APPLICATION_JSON)
public class DigitalObjectIdentifier {

    @Inject
    ParsersConfig parsers;

    private static final Logger LOG = Logger.getLogger(DigitalObjectIdentifier.class);


    /**
     * Prepare REST client for Zenodo.
     *
     * @param params will receive the parser
     * @return true on success, updates field "zenodo"
     */
    @PostConstruct
    private boolean getZenodoParser(ActionParameters params) {

        LOG.info("Obtaining REST client for Zenodo");

        if (null != params.zenodo)
            return true;

        // Check if Zenodo base URL is valid
        URL urlParser;
        try {
            urlParser = new URL(parsers.parsers().get("zenodo"));
        } catch (MalformedURLException e) {
            LOG.error(e.getMessage());
            return false;
        }

        // Create the REST client for the selected transfer service
        params.zenodo = RestClientBuilder.newBuilder()
                .baseUrl(urlParser)
                .build(Zenodo.class);

        return true;
    }

    /**
     * Parse Digital Object Identifier at specified URL and return list of files.
     *
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @GET
    @Path("/parser/zenodo")
    @SecurityRequirement(name = "none")
    @Operation(operationId = "parseZenodo",  summary = "Extract source files from Zenodo record in DOI")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = StorageContent.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "404", description="Record not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActionError.class)))
    })
    public Response parseZenodoDOI(@Parameter(description = "The DOI to parse, must point to Zenodo",  required = true,
                                              example = "https://doi.org/12.3456/zenodo.12345678")
                                   @RestQuery String doi) {

        LOG.infof("Parse Zenodo DOI %s", doi);

        try {
            ActionParameters ap = new ActionParameters();
            CompletableFuture<Response> response = new CompletableFuture<>();
            Uni<ActionParameters> start = Uni.createFrom().item(ap);
            start
                .onItem().transformToUni(params -> {
                    // Validate DOI
                    boolean isValid = null != doi && !doi.isEmpty();
                    if(isValid) {
                        Pattern p = Pattern.compile("^https?://([\\w\\.]+)/([\\w\\.]+)/zenodo\\.(\\d+).*", Pattern.CASE_INSENSITIVE);
                        Matcher m = p.matcher(doi);
                        isValid = m.matches();
                        if (isValid)
                            params.source = m.group(3);
                    }

                    if(!isValid) {
                        response.complete(new ActionError("invalidSource", Tuple2.of("doi", doi)).toResponse());
                        return Uni.createFrom().failure(new RuntimeException());
                    }

                    return Uni.createFrom().item(params);
                })
                .onItem().transformToUni(params -> {
                    // Create REST client for Zenodo
                    if (!getZenodoParser(params)) {
                        // Could not get REST client
                        response.complete(new ActionError("invalidParserConfig").toResponse());
                        return Uni.createFrom().failure(new RuntimeException());
                    }

                    return Uni.createFrom().item(params);
                })
                .onItem().transformToUni(params -> {
                    // Get Zenodo record details
                    return params.zenodo.getRecordsAsync(params.source);
                })
                .onItem().transformToUni(record -> {
                    // Got Zenodo record
                    LOG.infof("Got Zenodo record %s", record.id);

                    // Build list of source files
                    StorageContent srcFiles = new StorageContent();
                    for(var file : record.files) {
                        srcFiles.elements.add(new StorageElement(file));
                    }

                    srcFiles.count = srcFiles.elements.size();

                    // Success
                    response.complete(Response.ok(srcFiles).build());
                    return Uni.createFrom().nullItem();
                })
                .onFailure().invoke(e -> {
                    LOG.errorf("Failed to parse Zenodo DOI %s", doi);
                    if (!response.isDone())
                        response.complete(new ActionError(e, Tuple2.of("doi", doi))
                                .toResponse());
                })
                .subscribe().with(unused -> {});

            // Wait until parse completes (possibly with error)
            Response r = response.get();
            return r;
        } catch (InterruptedException e) {
            // Cancelled
            return new ActionError("parseZenodoDOIInterrupted").toResponse();
        } catch (ExecutionException e) {
            // Execution error
            return new ActionError("parseZenodoDOIExecutionError").toResponse();
        }
    }
}
