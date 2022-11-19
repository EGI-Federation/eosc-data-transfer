package parser.generic.model;


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
