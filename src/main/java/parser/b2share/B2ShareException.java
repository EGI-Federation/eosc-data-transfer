package parser.b2share;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;


/**
 * Exception class for B2Share API calls
 */
public class B2ShareException extends WebApplicationException {

    private String responseBody;

    public B2ShareException() {
        super();
    }

    /***
     * Construct from a response
     * @param resp Response object
     * @param body Response body
     */
    public B2ShareException(Response resp, String body) {
        super(resp);
        this.responseBody = body;
    }

    /***
     * Get the response body
     * @return Body of the response
     */
    String responseBody() { return responseBody; }
}
