package eosc.eu.model;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.jboss.resteasy.reactive.client.impl.ClientResponseImpl;
import org.jboss.resteasy.reactive.common.jaxrs.ResponseImpl;
//import org.jboss.resteasy.specimpl.AbstractBuiltResponse;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import egi.fts.FileTransferServiceException;
import eosc.eu.TransferServiceException;
import parser.zenodo.ZenodoException;


/**
 * An error in an operation of the REST API.
 * Can be constructed manually or from exceptions.
 *
 * This is the OpenApi entity that will be returned in case of error in the API.
 * The HTTP status will be returned as the status code of the failed endpoint and the other
 * members will be rendered as the body of the response of the failed endpoint.
 */
@Schema(name = "Error")
public class ActionError {

    @Schema(title="Error type")
    public String id;

    @Schema(title="Error message")
    @JsonInclude(Include.NON_EMPTY)
    public Optional<String> description;

    @Schema(title="Additional details about the error")
    @JsonInclude(Include.NON_EMPTY)
    public Optional<Map<String, String>> details;

    @JsonIgnore
    private Status status = defaultStatus();

    public static Status defaultStatus() { return Status.INTERNAL_SERVER_ERROR; }


    /**
     * Copy constructor does deep copy
     */
    public ActionError(ActionError error) {

        this.id = error.id;
        this.description = error.description;
        this.details = Optional.empty();

        if(error.details.isPresent()) {
            var ed = error.details.get();
            if(!ed.isEmpty()) {
                HashMap<String, String> details = new HashMap<>();
                details.putAll(ed);
                this.details = Optional.of(details);
            }
        }
    }

    /**
     * Copy but change id
     */
    public ActionError(ActionError error, String newId) {
        this(error);
        this.id = newId;
    }

    /**
     * Construct with error id
     */
    public ActionError(String id) {
        this.id = id;
        this.description = Optional.empty();
        this.details = Optional.empty();
    }

    /**
     * Construct with error id and description
     */
    public ActionError(String id, String description) {
        this.id = id;
        this.description = Optional.of(description);
        this.details = Optional.empty();
    }

    /**
     * Construct with error id and detail
     */
    public ActionError(String id, Tuple2<String, String> detail) {
        this(id, Arrays.asList(detail));
    }

    /**
     * Construct with error id and details
     */
    public ActionError(String id, List<Tuple2<String, String>> details) {
        this.id = id;
        this.description = Optional.empty();
        this.details = Optional.of(new HashMap<>() {
            {
                for(Tuple2<String, String> detail : details)
                    if(null != detail.getItem2() && !detail.getItem2().isEmpty())
                        put(detail.getItem1(), detail.getItem2());
            }
        });
    }

    /**
     * Construct with error id, description, and detail
     */
    public ActionError(String id, String description, Tuple2<String, String> detail) {
        this(id, description, Arrays.asList(detail));
    }

    /**
     * Construct with error id, description, and details
     */
    public ActionError(String id, String description, List<Tuple2<String, String>> details) {
        this.id = id;
        this.description = Optional.of(description);
        this.details = Optional.of(new HashMap<>() {
            {
                for(Tuple2<String, String> detail : details)
                    if(null != detail.getItem2() && !detail.getItem2().isEmpty())
                        put(detail.getItem1(), detail.getItem2());
            }
        });
    }

    /**
     * Construct from exception
     */
    public ActionError(Throwable t) {
        this.id = "exception";
        this.details = Optional.empty();

        String msg = t.getMessage();
        if(null != msg && !msg.isEmpty())
            this.description = Optional.of(msg);
        else
            this.description = Optional.empty();

        var type = t.getClass();
        if (type.equals(FileTransferServiceException.class) ||
            type.equals(ZenodoException.class) ||
            type.equals(ClientWebApplicationException.class) ||
            type.equals(WebApplicationException.class) ) {
            // Build from web exception
            var we = (WebApplicationException)t;
            this.status = Status.fromStatusCode(we.getResponse().getStatus());

            if(this.id.equals("exception")) {
                switch (this.status) {
                    case UNAUTHORIZED:
                        this.id = "notAuthenticated";
                        break;
                    case FORBIDDEN:
                        this.id = "noAccess";
                        break;
                    case BAD_REQUEST:
                        this.id = "badRequest";
                        break;
                    case NOT_FOUND:
                        this.id = "notFound";
                        break;
                }
            }

            if(this.description.isEmpty()) {
                String reason = we.getResponse().getStatusInfo().getReasonPhrase();
                if (null != reason && !reason.isEmpty())
                    this.description = Optional.of(reason);
            }
        }
        else if(type.equals(ProcessingException.class)) {
            // Build from processing exception
            var pe = (ProcessingException)t;
            this.id = "processingError";

            var cause = pe.getCause();
            if(null != cause) {
                msg = cause.getMessage();
                if(null != msg && !msg.isEmpty())
                    this.description = Optional.of(msg);
            }
        }

        if (type.equals(TransferServiceException.class)) {
            TransferServiceException tse = (TransferServiceException)t;
            this.id = tse.getId();
            if(this.id.equals("fieldNotSupported"))
                this.status = Status.BAD_REQUEST;

            // Collect the details from the exception (if any)
            var tseDetails = tse.getDetails();
            if(null != tseDetails && !tseDetails.isEmpty()) {
                HashMap<String, String> details = new HashMap<>();
                details.putAll(tseDetails);
                this.details = Optional.of(details);
            }
        }
    }

    /**
     * Construct from exception and detail
     */
    public ActionError(Throwable t, Tuple2<String, String> detail) {
        this(t, Arrays.asList(detail));
    }

    /**
     * Construct from exception and details
     */
    public ActionError(Throwable t, List<Tuple2<String, String>> details) {
        this(t);

        // Combine details (copied from throwable) with the extra ones
        Map<String, String> combinedDetails = new HashMap<>();
        for(Tuple2<String, String> detail : details)
            if(null != detail.getItem2() && !detail.getItem2().isEmpty())
                combinedDetails.put(detail.getItem1(), detail.getItem2());

        if(this.details.isPresent()) {
            var ed = this.details.get();
            if(!ed.isEmpty())
                combinedDetails.putAll(ed);
        }

        this.details = Optional.of(combinedDetails);

        // Adjust id for some statuses
        var type = t.getClass();
        if (type.equals(FileTransferServiceException.class) ||
            type.equals(ClientWebApplicationException.class) ||
            type.equals(WebApplicationException.class) ) {
            // Refine the id for NOT_FOUND errors
            switch(this.status) {
                case NOT_FOUND:
                    if(this.details.isPresent() && !this.details.get().isEmpty())
                    {
                        var keys = this.details.get().keySet();
                        if(keys.contains("fieldName"))
                            this.id = "fieldNotFound";
                        else if(keys.contains("jobId"))
                            this.id = "transferNotFound";
                        else if(keys.contains("seUrl"))
                            this.id = "storageElementNotFound";
                    } break;
            }
        }
        /*
        else if(type.equals(ConnectorException.class)) {
            switch(this.status) {
                case NOT_FOUND:
                    if(this.details.isPresent() && !this.details.get().isEmpty())
                    {
                        var keys = this.details.get().keySet();
                        if(keys.contains("catalogId"))
                            this.id = "catalogNotFound";
                        else if(keys.contains("resourceId"))
                            this.id = "resourceNotFound";
                        else if(keys.contains("representationId"))
                            this.id = "representationNotFound";
                        else if(keys.contains("artifactId"))
                            this.id = "artifactNotFound";
                        else if(keys.contains("ruleId"))
                            this.id = "ruleNotFound";
                        else if(keys.contains("contractId"))
                            this.id = "contractNotFound";
                    } break;
            }
        }
         */
    }

    /**
     * Retrieve the HTTP status code
     */
    public Status getStatus() {
        return this.status;
    }

    /**
     * Update the HTTP status code
     */
    public ActionError setStatus(Status status) {
        this.status = status;
        return this;
    }

    /**
     * Convert to Response that can be returned by a REST endpoint
     */
    public Response toResponse() {
        return Response.ok(this).status(this.status).build();
    }

    /**
     * Convert to Response with new status that can be returned by a REST endpoint
     */
    public Response toResponse(Status status) {
        return Response.ok(this).status(status).build();
    }
}
