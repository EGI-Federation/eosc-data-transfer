package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * Estimation of the cost (in credits) of a data transfer
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferEstimationInfo {

    @Schema(description="Amount of data to be transferred")
    public long bytes;

    @Schema(description="Cost in credits (rounded up)")
    public long credits;

    @Schema(description="Cost in credits")
    public double creditsExact;


    /**
     * Constructor
     */
    public TransferEstimationInfo() {
        this.bytes = 0;
        this.credits = 0;
        this.creditsExact = 0;
    }

    /***
     * Account for another file in the estimation.
     * @param fileSize Size of the file in bytes.
     */
    public void addFile(long fileSize) { this.bytes += fileSize; }

    /***
     * Calculate cost of transfer in credits
     * @param bytesPerCredit says how many bytes can be transferred for one credit
     * @return true on success
     */
    public boolean calculateCost(long bytesPerCredit) {
        if(bytesPerCredit <= 0)
            return false;

        this.credits = (this.bytes + bytesPerCredit - 1) / bytesPerCredit;
        this.creditsExact = (double)this.bytes / bytesPerCredit;
        return true;
    }

}
