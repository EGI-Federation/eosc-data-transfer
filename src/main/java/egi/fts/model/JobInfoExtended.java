package egi.fts.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.Map;
import java.util.Optional;


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
    public String verify_checksum; // "b" or "n"

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Boolean> overwrite_flag;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> priority;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> retry;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> retry_delay;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> max_time_in_queue;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> copy_pin_lifetime;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> bring_online;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> target_qos;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Boolean> cancel_job;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date job_finished;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
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
    public JobInfoExtended() {
        overwrite_flag = Optional.empty();
        priority = Optional.empty();
        retry = Optional.empty();
        retry_delay = Optional.empty();
        max_time_in_queue = Optional.empty();
        copy_pin_lifetime = Optional.empty();
        bring_online = Optional.empty();
        target_qos = Optional.empty();
        cancel_job = Optional.empty();
    }
}
