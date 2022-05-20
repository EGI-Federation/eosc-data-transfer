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

    public ZenodoException(Response response, String responseBody) {
        super(response);
        this.responseBody = responseBody;
    }

    String responseBody() { return responseBody; }
}
