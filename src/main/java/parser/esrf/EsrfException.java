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

    public EsrfException(Response resp, String body) {
        super(resp);
        this.responseBody = body;
    }

    String responseBody() { return responseBody; }
}
