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

    private static final Logger LOG = Logger.getLogger(ParserHelper.class);
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
     * Check if a URL is being redirected
     * @param url URL to request
     * @return the URL where the passed in URL is redirected, null if not redirected
     */
    public Uni<String> checkRedirect(String url) {

        var result = client.headAbs(url)
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
                LOG.errorf("Error in request HEAD %s", url);
            });

        return result;
    }

    /***
     * Get the response HTTP headers from passed in URL
     * @param url URL to request
     * @return HTTP headers
     */
    public Uni<Tuple2<String, MultiMap>> fetchHeaders(String url) {

        var result = client.headAbs(url)
            .send()
            .chain(resp -> {
                var urlTarget = url;
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
                LOG.errorf("Error in request HEAD %s", url);
            });

        return result;
    }

    /***
     * Fetch the Linkset from passed in URL
     * @param url URL to request
     * @return Parsed Linkset
     */
    public Uni<StorageContent> fetchLinkset(String url) {

        var result = client.getAbs(url)
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
                LOG.errorf("Error in request HEAD %s", url);
            });

        return result;
    }

}
