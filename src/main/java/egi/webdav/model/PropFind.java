package egi.webdav.model;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlElement;


/***
 * Class for PROPFIND requests
 */
@XmlRootElement(name = "propfind", namespace="DAV:")
public class PropFind {

    @XmlElement(name = "allprop", namespace="DAV:")
    public String allprop;


    /***
     * Construct with empty element
     */
    public PropFind() { this.allprop = ""; }
}
