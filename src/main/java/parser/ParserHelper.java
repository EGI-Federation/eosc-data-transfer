package parser;

import io.smallrye.mutiny.tuples.Tuple2;
import io.vertx.mutiny.core.MultiMap;
import io.vertx.mutiny.ext.web.client.WebClient;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;

import eosc.eu.model.StorageContent;
import eosc.eu.model.StorageElement;


/***
 * Class for simple web requests
 */
public class ParserHelper {

    private static final Logger log = Logger.getLogger(ParserHelper.class);
    private WebClient client;
    private String redirectedToUrl;
    private MultiMap headers;


    /***
     * Constructor to wrap a Web client
     * @param client The client to use to make web requests
     */
    public ParserHelper(WebClient client) {
        this.client = client;
    }

    /***
     * Get the cached redirected to URL
     * @return the URL where the checked URL was redirected, null if not redirected
     */
    public String redirectedToUrl() { return this.redirectedToUrl; }

    /***
     * Get the cached response HTTP headers
     * @return HTTP headers
     */
    public MultiMap headers() { return this.headers; }

    /***
     * Convert canonical DOI to regular URL (with HTTPS schema)
     * @param uri URI with "doi" schema (doi://...)
     * @return URL to doi.org or the unchanged URI if it is not using the "doi" schema
     */
    private String doiToUrl(String uri) {
        return uri.replace("doi://", "https://doi.org/");
    }

    /***
     * Check if URI is being redirected
     * @param uri URI to request, can start with doi://
     * @return the URL where the passed in URI is redirected, null if not redirected
     */
    public Uni<String> checkRedirect(String uri) {

        var result = client.headAbs(doiToUrl(uri))
            .send()
            .chain(resp -> {
                var redirects = resp.followedRedirects();
                if(!redirects.isEmpty()) {
                    // Redirected
                    this.headers = resp.headers();
                    this.redirectedToUrl = redirects.get(redirects.size() - 1);
                    return Uni.createFrom().item(this.redirectedToUrl);
                }

                // Not redirected
                return Uni.createFrom().nullItem();
            })
            .onFailure().invoke(e -> {
                log.errorf("Error in request HEAD %s", uri);
            });

        return result;
    }

    /***
     * Get the response HTTP headers from passed in URI
     * @param uri URI to request, can start with doi://
     * @return HTTP headers
     */
    public Uni<Tuple2<String, MultiMap>> fetchHeaders(String uri) {

        var result = client.headAbs(doiToUrl(uri))
            .send()
            .chain(resp -> {
                var urlTarget = uri;
                var redirects = resp.followedRedirects();
                if(!redirects.isEmpty()) {
                    // Redirected
                    this.redirectedToUrl = redirects.get(redirects.size() - 1);
                    urlTarget = this.redirectedToUrl;
                }

                this.headers = resp.headers();

                return Uni.createFrom().item(Tuple2.of(urlTarget, this.headers));
            })
            .onFailure().invoke(e -> {
                log.errorf("Error in request HEAD %s", uri);
            });

        return result;
    }

    /***
     * Fetch the Linkset from passed in URI
     * @param uri URI to request, can start with doi://
     * @return Parsed Linkset
     */
    public Uni<StorageContent> fetchLinkset(String uri) {

        var result = client.getAbs(doiToUrl(uri))
            .send()
            .onItem().transform(resp -> resp.bodyAsJsonObject())
            .chain(json -> {
                // Got a linkset
                var content = new StorageContent();
                var linkset = json.getJsonObject("linkset");
                var items = (null != linkset) ? linkset.getJsonArray("item") : null;

                if(null != items) {
                    // Got items from the linkset
                    for(int i = 0; i < items.size(); i++) {
                        var item = items.getJsonObject(i);
                        var href = item.getString("href");
                        if(null != href)
                            content.add(new StorageElement(href, item.getString("type")));
                    }
                }

                return Uni.createFrom().item(content);
            })
            .onFailure().invoke(e -> {
                log.errorf("Error in request HEAD %s", uri);
            });

        return result;
    }

}
