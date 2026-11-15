package eosc.eu;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.Optional;


/**
 * The successful result of an operation in the REST API.
 */
@Schema(name = "Success", description = "Confirmation of a completed storage operation")
public class ActionSuccess {

    @Schema(description="Confirmation message", example = "Folder created")
    @JsonInclude(Include.NON_EMPTY)
    public Optional<String> message;

    /**
     * Constructor
     */
    public ActionSuccess() {}

    /**
     * Construct from message
     * @param message Confirmation message to return
     */
    public ActionSuccess(String message) { this.message = Optional.of(message); }

    /**
     * Convert to Response that can be returned by a REST endpoint
     * @return Response object
     */
    public Response toResponse() {
        return Response.ok(this).build();
    }

    /**
     * Convert to Response with new status that can be returned by a REST endpoint
     * @param status New HTTP status
     * @return Response object with the new status
     */
    public Response toResponse(Status status) {
        return Response.ok(this).status(status).build();
    }
}
