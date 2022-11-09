package parser.b2share.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


/**
 * Title in a B2Share record's metadata
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2ShareTitle {

    public String title;
}
