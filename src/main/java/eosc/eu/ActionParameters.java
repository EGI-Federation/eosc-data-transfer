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

    public IParserService parser;
    public ITransferService ts;
    public IStorageService ss;
    public String destination;  // Destination key
    public String authType;     // See Transfer.AuthorizeWith
    public Response response;


    /**
     * Constructor
     */
    public ActionParameters() {
        this.response = Response.ok().build();
    }

    /**
     * Construct with destination
     * @param destination The destination for this action
     */
    public ActionParameters(String destination) {
        this.destination = destination;
        this.response = Response.ok().build();
    }

    /**
     * Copy constructor
     * @param ap Parameters to copy
     */
    public ActionParameters(ActionParameters ap) {
        this.parser = ap.parser;
        this.ts = ap.ts;
        this.ss = ap.ss;
        this.destination = ap.destination;
        this.authType = ap.authType;
        this.response = ap.response;
    }

    /**
     * Check if the embedded API Response is a success
     * @return true if action was successful
     */
    public boolean succeeded() {
        return Family.familyOf(this.response.getStatus()) == Family.SUCCESSFUL;
    }

    /**
     * Check if the embedded API Response is a failure
     * @return true if action failed
     */
    public boolean failed() {
        return Family.familyOf(this.response.getStatus()) != Family.SUCCESSFUL;
    }

    /**
     * Get failure Uni
     * @param <T> is the type of the Uni
     * @return runtime exception as Uni
     */
    public static <T> Uni<T> failedUni() {
        return Uni.createFrom().failure(new RuntimeException());
    }

    /**
     * Get failure Multi
     * @param <T> is the type of the Multi
     * @return runtime exception as Multi
     */
    public static <T> Multi<T> failedMulti() {
        return Multi.createFrom().failure(new RuntimeException());
    }
}
