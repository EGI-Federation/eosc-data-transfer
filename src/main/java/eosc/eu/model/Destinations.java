package eosc.eu.model;

import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;


/**
 * Collection of all supported destinations
 */
@Schema(description = "All destination storage types configured on this instance")
public class Destinations extends ArrayList<DestinationInfo> {

    /***
     * Constructor
     */
    public Destinations() {
        super();
    }

    /***
     * Construct with specified capacity
     * @param initialCapacity Initial capacity of the list
     */
    public Destinations(int initialCapacity) {
        super(initialCapacity);
    }

    /***
     * Convert to Response
     * @return Response object
     */
    public Response toResponse() {
        return Response.ok(this).build();
    }

}
