package egi.fts.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.Map;


/**
 * Details of a transfer job
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobInfoExtended extends JobInfo {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String job_state;    // "ACTIVE"

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String job_type;     // "N"

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> job_metadata;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String source_se;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String source_space_token;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String destination_se;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String space_token;

    public boolean dst_file_report;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String verify_checksum; // "y" or "n"
    public boolean overwrite_flag;
    public int priority;
    public int retry;
    public int retry_delay;
    public int max_time_in_queue;
    public int copy_pin_lifetime;
    public int bring_online;
    public int target_qos;
    public boolean cancel_job;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Date job_finished;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Date submit_time;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String submit_host;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String http_status; // "200 OK"

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String reason;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String vo_name;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String user_dn;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String cred_id;


    /**
     * Constructor
     */
    public JobInfoExtended() {}
}
