package egi.fts;

import jakarta.annotation.Priority;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.jboss.resteasy.reactive.common.jaxrs.ResponseImpl;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode;

import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayInputStream;


/**
 * Custom exception mapper for FileTransferService API calls, allows access to response body in case of error
 */
@Priority(Priorities.USER)
public final class FileTransferServiceExceptionMapper implements ResponseExceptionMapper<FileTransferServiceException> {

    @Override
    public FileTransferServiceException toThrowable(Response response) {
        try {
            response.bufferEntity();
        } catch(Exception ignored) {}

        String msg = getBody(response);
        return new FileTransferServiceException(response, msg);
    }

    @Override
    public boolean handles(int status, MultivaluedMap<String, Object> headers) {
        return status >= StatusCode.BAD_REQUEST;
    }

    private String getBody(Response response) {
        String body = "";
        if(response.hasEntity()) {
            ByteArrayInputStream is = (ByteArrayInputStream)response.getEntity();
            if(null == is)
                is = (ByteArrayInputStream)((ResponseImpl)response).getEntityStream();
            byte[] bytes = new byte[is.available()];
            is.read(bytes, 0, is.available());
            body = new String(bytes);
        }
        return body;
    }
}
