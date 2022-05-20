package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * Parameters of a transfer job
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferParameters {

    public boolean verifyChecksum = false;
    public boolean overwrite = false;
    public int retry = 1;

    @Schema(title="Transfer priority from 1 to 5, 1 being the lowest priority")
    public int priority;

    @Schema(title="Disable all checks, just copy the file")
    public boolean strictCopy = false;

    @Schema(title="Force IPv4 if the underlying protocol supports it")
    public boolean ipv4 = false;

    @Schema(title="Force IPv6 if the underlying protocol supports it")
    public boolean ipv6 = false;


    /**
     * Constructor
     */
    public TransferParameters() {}
}
