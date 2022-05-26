package eosc.eu.model;

import egi.fts.model.JobInfoExtended;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.ArrayList;



/**
 * List of data transfer jobs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferList {

    public String kind = "TransferList";
    public List<TransferInfoExtended> jobs;

    /**
     * Construct from list of FTS job infos
     */
    public TransferList() {

        this.jobs = new ArrayList<>();
    }
}
