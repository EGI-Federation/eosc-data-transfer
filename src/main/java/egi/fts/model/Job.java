package egi.fts.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

import eosc.eu.model.Transfer;


/**
 * A new FTS transfer job
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Job {

    public List<JobFileSet> files;
    public JobParameters params;

    /**
     * Constructor
     */
    public Job() { this.files = new ArrayList<>(); }

    /**
     * Construct from generic transfer
     */
    public Job(Transfer transfer) {
        this.params = new JobParameters(transfer.params);
        this.files = new ArrayList<>(transfer.files.size());
        if(null != transfer.files) {
            for(var fset : transfer.files)
                this.files.add(new JobFileSet(fset));
        }
    }
}
