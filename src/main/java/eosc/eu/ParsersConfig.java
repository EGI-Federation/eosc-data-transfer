package eosc.eu;

import io.smallrye.config.ConfigMapping;

import java.util.*;

/***
 * The configuration of the parsers
 */
@ConfigMapping(prefix = "proxy")
public interface ParsersConfig {

    public Map<String, String> parsers();
}
