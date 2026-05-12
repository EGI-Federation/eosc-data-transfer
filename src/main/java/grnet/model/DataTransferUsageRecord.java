package grnet.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.Optional;


/**
 * Usage record for a data transfer
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataTransferUsageRecord {

    public String metric_definition_id;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssX")
    public Date time_period_start;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssX")
    public Date time_period_end;

    public long value;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<String> group_id;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<String> user_id;


    /***
     * Constructor
     */
    public DataTransferUsageRecord() {
        this.group_id = Optional.empty();
        this.user_id = Optional.empty();
    }

    /**
     * Construct from metric id and data amount
     */
    public DataTransferUsageRecord(String metricId, long bytes, Date startAt, Date endAt) {
        this.metric_definition_id = metricId;
        this.value = bytes;
        this.group_id = Optional.empty();
        this.user_id = Optional.empty();
        this.time_period_start = startAt;
        this.time_period_end = endAt;
    }

    /**
     * Construct from metric id, data amount, and user id
     */
    public DataTransferUsageRecord(String metricId, long bytes, Date startAt, Date endAt, String voPersonId) {
        this.metric_definition_id = metricId;
        this.value = bytes;
        this.group_id = Optional.empty();
        this.user_id = Optional.of(voPersonId);
        this.time_period_start = startAt;
        this.time_period_end = endAt;
    }
}
