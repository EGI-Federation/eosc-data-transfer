package cern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;


/**
 * Exception class for FileTransferService API calls
 */
public class FileTransferServiceException extends WebApplicationException {

    String responseBody;
    FileTransferServiceError error;

    public FileTransferServiceException() {
        super();
    }

    public FileTransferServiceException(Response resp, String body) {
        super(resp);
        this.responseBody = body;

        var mapper = new ObjectMapper();

        try {
            error = mapper.readValue(body, FileTransferServiceError.class);
        } catch(JsonProcessingException e) {}
    }

    String responseBody() { return responseBody; }
    String errorDetail() { return null != error ? error.http_message : super.getMessage(); }


    /***
     * Class to map the error returned by FTS
     */
    class FileTransferServiceError {
        public String job_id;
        public String http_status;
        public String http_message;
    }
}
