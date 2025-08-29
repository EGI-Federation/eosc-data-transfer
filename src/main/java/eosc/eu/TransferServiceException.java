package eosc.eu;

import io.smallrye.mutiny.tuples.Tuple2;

import java.util.*;


/**
 * Exception class for generic transfer service
 */
public class TransferServiceException extends RuntimeException {

    private final String id;
    private Map<String, String> details;
    private Optional<Integer> code = Optional.empty();


    /**
     * Construct with error id
     */
    public TransferServiceException(String id) {
        this.id = id;
    }

    /**
     * Construct with error id and message
     */
    public TransferServiceException(String id, String message) {
        super(message);
        this.id = id;
    }

    /**
     * Construct with error id, status code, and message
     */
    public TransferServiceException(String id, int code, String message) {
        super(message);
        this.id = id;

        if(0 != code)
            this.code = Optional.of(code);
    }

    /**
     * Construct with error id and detail
     */
    public TransferServiceException(String id, Tuple2<String, String> detail) {
        this(id, 0, Arrays.asList(detail));
    }

    /**
     * Construct with error id, status code, and detail
     */
    public TransferServiceException(String id, int code, Tuple2<String, String> detail) {
        this(id, code, Arrays.asList(detail));
    }

    /**
     * Construct with error id and details
     */
    public TransferServiceException(String id, List<Tuple2<String, String>> details) {
        this(id, 0, details);
    }

    /**
     * Construct with error id, status code, and details
     */
    public TransferServiceException(String id, int code, List<Tuple2<String, String>> details) {
        this.id = id;
        this.details = new HashMap<>() {
            {
                for(Tuple2<String, String> detail : details)
                    if(null != detail.getItem2() && !detail.getItem2().isEmpty())
                        put(detail.getItem1(), detail.getItem2());
            }
        };

        if(0 != code)
            this.code = Optional.of(code);
    }

    /**
     * Construct with exception and error id
     */
    public TransferServiceException(Throwable e, String id) {
        super(e);
        this.id = id;
    }

    /**
     * Construct with exception, error id and detail
     */
    public TransferServiceException(Throwable e, String id, Tuple2<String, String> detail) {
        this(e, id, Arrays.asList(detail));
    }

    /**
     * Construct with exception, error id and details
     */
    public TransferServiceException(Throwable e, String id, List<Tuple2<String, String>> details) {
        super(e);
        this.id = id;
        this.details = new HashMap<>() {
            {
                for(Tuple2<String, String> detail : details)
                    if(null != detail.getItem2() && !detail.getItem2().isEmpty())
                        put(detail.getItem1(), detail.getItem2());
            }
        };
    }

    public String getId() { return id; }

    public Map<String, String> getDetails() { return details; }

    public boolean hasCode() { return code.isPresent(); }

    public int getCode() { return code.orElse(0); }
}
