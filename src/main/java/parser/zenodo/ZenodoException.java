package parser.zenodo;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;


/**
 * Exception class for Zenodo API calls
 */
public class ZenodoException extends WebApplicationException {

    private String responseBody;

    public ZenodoException() {
        super();
    }

    /***
     * Construct from a response
     * @param resp Response object
     * @param body Response body
     */
    public ZenodoException(Response resp, String body) {
        super(resp);
        this.responseBody = body;
    }

    String responseBody() { return responseBody; }
}
