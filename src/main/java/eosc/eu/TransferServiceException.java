package eosc.eu;


/**
 * Exception class for generic transfer service
 */
public class TransferServiceException extends RuntimeException {

    private String id;

    public TransferServiceException(String id) {
        this.id = id;
    }

    public TransferServiceException(String id, Throwable e) {
        super(e);
        this.id = id;
    }

    public String getId() { return id; }
}
