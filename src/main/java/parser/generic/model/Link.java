package parser.generic.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Details of a Link header
 */
public class Link {

    public String url;
    public String relation;
    public String type;

    /***
     * Construct from elements
     */
    public Link(String url, String relation, String type) {
        this.url = url;
        this.relation = relation;
        this.type = type;
    }
}
