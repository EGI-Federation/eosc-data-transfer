package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;


/**
 * The successful result of an operation in the REST API.
 */
@Schema(name = "Success")
public class ActionSuccess {

    @Schema(title="Confirmation message")
    @JsonInclude(Include.NON_EMPTY)
    public Optional<String> message;


    /**
     * Constructor
     */
    public ActionSuccess() {}

    /**
     * Construct from message
     */
    public ActionSuccess(String message) { this.message = Optional.of(message); }

    /**
     * Convert to Response that can be returned by a REST endpoint
     */
    public Response toResponse() {
        return Response.ok(this).build();
    }

    /**
     * Convert to Response with new status that can be returned by a REST endpoint
     */
    public Response toResponse(Status status) {
        return Response.ok(this).status(status).build();
    }
}
