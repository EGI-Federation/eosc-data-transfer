package eosc.eu.model;

import jakarta.ws.rs.core.Response;
import java.util.ArrayList;


/**
 * Collection of all supported destinations
 */
public class Destinations extends ArrayList<DestinationInfo> {

    /***
     * Constructor
     */
    public Destinations() {
        super();
    }

    /***
     * Construct with specified capacity
     */
    public Destinations(int initialCapacity) {
        super(initialCapacity);
    }

    /***
     * Convert to Response
     */
    public Response toResponse() {
        return Response.ok(this).build();
    }

}
