package parser.esrf;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;


/**
 * Exception class for ESRF API calls
 */
public class EsrfException extends WebApplicationException {

    private String responseBody;

    public EsrfException() {
        super();
    }

    /***
     * Construct from a response
     * @param resp Response object
     * @param body Response body
     */
    public EsrfException(Response resp, String body) {
        super(resp);
        this.responseBody = body;
    }

    /***
     * Get the response body
     * @return Body of the response
     */
    String responseBody() { return responseBody; }
}
