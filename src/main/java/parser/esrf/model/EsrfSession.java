package parser.esrf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


/**
 * ESRF session
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EsrfSession {

    public String sessionId;
    public double lifeTimeMinutes;
    public String name;
    public boolean isAdministrator;
    public boolean isInstrumentScientist;
    public boolean isMinter;

    /***
     * Constructor
     */
    public EsrfSession() {}
}
