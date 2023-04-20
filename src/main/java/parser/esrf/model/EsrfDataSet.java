package parser.esrf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;


/**
 * ESRF dataset
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EsrfDataSet {

    public String id;
    public String name;
    public Date startDate;
    public Date endDate;
    public String location;

    /***
     * Constructor
     */
    public EsrfDataSet() {}
}
