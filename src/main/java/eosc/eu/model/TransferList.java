package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.ArrayList;

import egi.fts.model.JobInfoExtended;


/**
 * List of data transfer jobs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferList {

    public String kind = "TransferList";
    public int count;
    public List<TransferInfoExtended> transfers;

    /**
     * Construct from list of FTS job infos
     */
    public TransferList(List<JobInfoExtended> jobs) {
        this.count = jobs.size();
        this.transfers = new ArrayList<>(this.count);
        for(var job : jobs) {
            this.transfers.add(new TransferInfoExtended(job));
        }
    }
}
