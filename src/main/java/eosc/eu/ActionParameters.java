package eosc.eu;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;


/**
 * The parameters of an action to perform.
 * Allows implementing actions as instances of Function<JobInfo, Response>.
 *
 */
public class ActionParameters {

    public ParserService parser;
    public TransferService ts;
    public StorageService ss;
    public String destination;  // Destination key
    public Response response;


    /**
     * Constructor
     */
    public ActionParameters() {
        this.response = Response.ok().build();
    }

    /**
     * Construct with destination
     */
    public ActionParameters(String destination) {
        this.destination = destination;
        this.response = Response.ok().build();
    }

    /**
     * Copy constructor
     */
    public ActionParameters(ActionParameters ap) {
        this.parser = ap.parser;
        this.ts = ap.ts;
        this.ss = ap.ss;
        this.destination = ap.destination;
        this.response = ap.response;
    }

    /**
     * Check if the embedded API Response is a success
     */
    public boolean succeeded() {
        return Family.familyOf(this.response.getStatus()) == Family.SUCCESSFUL;
    }

    /**
     * Check if the embedded API Response is a failure
     */
    public boolean failed() {
        return Family.familyOf(this.response.getStatus()) != Family.SUCCESSFUL;
    }

    /**
     * Get failure Uni
     */
    public static <T> Uni<T> failedUni() {
        return Uni.createFrom().failure(new RuntimeException());
    }

    /**
     * Get failure Multi
     */
    public static <T> Multi<T> failedMulti() {
        return Multi.createFrom().failure(new RuntimeException());
    }
}
