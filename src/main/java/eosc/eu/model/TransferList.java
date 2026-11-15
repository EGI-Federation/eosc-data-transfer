package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.ArrayList;

import cern.model.JobInfoExtended;


/**
 * List of data transfer jobs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Transfers matching a search")
public class TransferList {

    @Schema(description = "Type discriminator of this schema", example = "TransferList")
    public String kind = "TransferList";

    @Schema(description = "Number of entries in 'transfers'")
    public int count;

    @Schema(description = "The matching transfers")
    public List<TransferInfoExtended> transfers;

    /**
     * Construct from list of FTS job infos
     * @param jobs The jobs that matched the search
     */
    public TransferList(List<JobInfoExtended> jobs) {
        this.count = jobs.size();
        this.transfers = new ArrayList<>(this.count);
        for(var job : jobs) {
            this.transfers.add(new TransferInfoExtended(job));
        }
    }
}
