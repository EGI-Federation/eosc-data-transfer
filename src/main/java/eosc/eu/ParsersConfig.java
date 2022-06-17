package eosc.eu;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.*;

/***
 * The configuration of the parsers
 */
@ConfigMapping(prefix = "proxy")
public interface ParsersConfig {

    // Contains the details of each specific parser
    public Map<String, ParserConfig> parsers();


    /***
     * The configuration of a parser
     */
    public interface ParserConfig {
        public String name();
        public String url();

        @WithDefault("5000")
        public int timeout(); // milliseconds

        @WithName("class")
        public String className();
    }
}
