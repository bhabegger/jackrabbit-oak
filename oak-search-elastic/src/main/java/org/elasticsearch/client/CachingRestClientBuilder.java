package org.elasticsearch.client;

import co.elastic.clients.util.LanguageRuntimeVersions;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.cache.HttpCacheContext;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpAsyncClient;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.client.HttpAsyncClient;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.VersionInfo;

import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
public class CachingRestClientBuilder {

    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 1000;
    public static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 30000;
    public static final int DEFAULT_MAX_CONN_PER_ROUTE = 10;
    public static final int DEFAULT_MAX_CONN_TOTAL = 30;

    static final String THREAD_NAME_PREFIX = "elasticsearch-rest-client-";
    private static final String THREAD_NAME_FORMAT = THREAD_NAME_PREFIX + "%d-thread-%d";

    public static final String VERSION;
    static final String META_HEADER_NAME = "X-Elastic-Client-Meta";
    static final String META_HEADER_VALUE;
    private static final String USER_AGENT_HEADER_VALUE;

    private static final Header[] EMPTY_HEADERS = new Header[0];

    private final List<Node> nodes;
    private Header[] defaultHeaders = EMPTY_HEADERS;
    private RestClient.FailureListener failureListener;
    private HttpClientConfigCallback httpClientConfigCallback;
    private RequestConfigCallback requestConfigCallback;
    private String pathPrefix;
    private NodeSelector nodeSelector = NodeSelector.ANY;
    private boolean strictDeprecationMode = false;
    private boolean compressionEnabled = false;
    private boolean metaHeaderEnabled = true;

    static {

        // Never fail on unknown version, even if an environment messed up their classpath enough that we can't find it.
        // Better have incomplete telemetry than crashing user applications.
        String version = null;
        try (InputStream is = RestClient.class.getResourceAsStream("version.properties")) {
            if (is != null) {
                Properties versions = new Properties();
                versions.load(is);
                version = versions.getProperty("elasticsearch-client");
            }
        } catch (IOException e) {
            // Keep version unknown
        }

        if (version == null) {
            version = ""; // unknown values are reported as empty strings in X-Elastic-Client-Meta
        }

        VERSION = version;

        USER_AGENT_HEADER_VALUE = String.format(
                Locale.ROOT,
                "elasticsearch-java/%s (Java/%s)",
                VERSION.isEmpty() ? "Unknown" : VERSION,
                System.getProperty("java.version")
        );

        VersionInfo httpClientVersion = null;
        try {
            httpClientVersion = AccessController.doPrivileged(
                    (PrivilegedAction<VersionInfo>) () -> VersionInfo.loadVersionInfo(
                            "org.apache.http.nio.client",
                            HttpAsyncClientBuilder.class.getClassLoader()
                    )
            );
        } catch (Exception e) {
            // Keep unknown
        }

        // Use a single 'p' suffix for all prerelease versions (snapshot, beta, etc).
        String metaVersion = version;
        int dashPos = metaVersion.indexOf('-');
        if (dashPos > 0) {
            metaVersion = metaVersion.substring(0, dashPos) + "p";
        }

        // service, language, transport, followed by additional information
        META_HEADER_VALUE = "es="
                + metaVersion
                + ",jv="
                + System.getProperty("java.specification.version")
                + ",t="
                + metaVersion
                + ",hc="
                + (httpClientVersion == null ? "" : httpClientVersion.getRelease())
                + LanguageRuntimeVersions.getRuntimeMetadata();
    }

    public CachingRestClientBuilder(HttpHost... hosts) {
        this(Arrays.stream(hosts).map(Node::new).collect(Collectors.toList()));
    }
    /**
     * Creates a new builder instance and sets the hosts that the client will send requests to.
     *
     * @throws IllegalArgumentException if {@code nodes} is {@code null} or empty.
     */
    CachingRestClientBuilder(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("nodes must not be null or empty");
        }
        for (Node node : nodes) {
            if (node == null) {
                throw new IllegalArgumentException("node cannot be null");
            }
        }
        this.nodes = nodes;
    }

    /**
     * Sets the default request headers, which will be sent along with each request.
     * <p>
     * Request-time headers will always overwrite any default headers.
     *
     * @throws NullPointerException if {@code defaultHeaders} or any header is {@code null}.
     */
    public CachingRestClientBuilder setDefaultHeaders(Header[] defaultHeaders) {
        Objects.requireNonNull(defaultHeaders, "defaultHeaders must not be null");
        for (Header defaultHeader : defaultHeaders) {
            Objects.requireNonNull(defaultHeader, "default header must not be null");
        }
        this.defaultHeaders = defaultHeaders;
        return this;
    }

    /**
     * Sets the {@link RestClient.FailureListener} to be notified for each request failure
     *
     * @throws NullPointerException if {@code failureListener} is {@code null}.
     */
    public CachingRestClientBuilder setFailureListener(RestClient.FailureListener failureListener) {
        Objects.requireNonNull(failureListener, "failureListener must not be null");
        this.failureListener = failureListener;
        return this;
    }

    /**
     * Sets the {@link CachingRestClientBuilder.HttpClientConfigCallback} to be used to customize http client configuration
     *
     * @throws NullPointerException if {@code httpClientConfigCallback} is {@code null}.
     */
    public CachingRestClientBuilder setHttpClientConfigCallback(HttpClientConfigCallback httpClientConfigCallback) {
        Objects.requireNonNull(httpClientConfigCallback, "httpClientConfigCallback must not be null");
        this.httpClientConfigCallback = httpClientConfigCallback;
        return this;
    }

    /**
     * Sets the {@link org.elasticsearch.client.RestClientBuilder.RequestConfigCallback} to be used to customize http client configuration
     *
     * @throws NullPointerException if {@code requestConfigCallback} is {@code null}.
     */
    public CachingRestClientBuilder setRequestConfigCallback(RequestConfigCallback requestConfigCallback) {
        Objects.requireNonNull(requestConfigCallback, "requestConfigCallback must not be null");
        this.requestConfigCallback = requestConfigCallback;
        return this;
    }

    /**
     * Sets the path's prefix for every request used by the http client.
     * <p>
     * For example, if this is set to "/my/path", then any client request will become <code>"/my/path/" + endpoint</code>.
     * <p>
     * In essence, every request's {@code endpoint} is prefixed by this {@code pathPrefix}. The path prefix is useful for when
     * Elasticsearch is behind a proxy that provides a base path or a proxy that requires all paths to start with '/';
     * it is not intended for other purposes and it should not be supplied in other scenarios.
     *
     * @throws NullPointerException if {@code pathPrefix} is {@code null}.
     * @throws IllegalArgumentException if {@code pathPrefix} is empty, or ends with more than one '/'.
     */
    public CachingRestClientBuilder setPathPrefix(String pathPrefix) {
        this.pathPrefix = cleanPathPrefix(pathPrefix);
        return this;
    }

    public static String cleanPathPrefix(String pathPrefix) {
        Objects.requireNonNull(pathPrefix, "pathPrefix must not be null");

        if (pathPrefix.isEmpty()) {
            throw new IllegalArgumentException("pathPrefix must not be empty");
        }

        String cleanPathPrefix = pathPrefix;
        if (cleanPathPrefix.startsWith("/") == false) {
            cleanPathPrefix = "/" + cleanPathPrefix;
        }

        // best effort to ensure that it looks like "/base/path" rather than "/base/path/"
        if (cleanPathPrefix.endsWith("/") && cleanPathPrefix.length() > 1) {
            cleanPathPrefix = cleanPathPrefix.substring(0, cleanPathPrefix.length() - 1);

            if (cleanPathPrefix.endsWith("/")) {
                throw new IllegalArgumentException("pathPrefix is malformed. too many trailing slashes: [" + pathPrefix + "]");
            }
        }
        return cleanPathPrefix;
    }

    /**
     * Sets the {@link NodeSelector} to be used for all requests.
     * @throws NullPointerException if the provided nodeSelector is null
     */
    public CachingRestClientBuilder setNodeSelector(NodeSelector nodeSelector) {
        Objects.requireNonNull(nodeSelector, "nodeSelector must not be null");
        this.nodeSelector = nodeSelector;
        return this;
    }

    /**
     * Whether the REST client should return any response containing at least
     * one warning header as a failure.
     */
    public CachingRestClientBuilder setStrictDeprecationMode(boolean strictDeprecationMode) {
        this.strictDeprecationMode = strictDeprecationMode;
        return this;
    }

    /**
     * Whether the REST client should compress requests using gzip content encoding and add the "Accept-Encoding: gzip"
     * header to receive compressed responses.
     */
    public CachingRestClientBuilder setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
        return this;
    }

    /**
     * Whether to send a {@code X-Elastic-Client-Meta} header that describes the runtime environment. It contains
     * information that is similar to what could be found in {@code User-Agent}. Using a separate header allows
     * applications to use {@code User-Agent} for their own needs, e.g. to identify application version or other
     * environment information. Defaults to {@code true}.
     */
    public CachingRestClientBuilder setMetaHeaderEnabled(boolean metadataEnabled) {
        this.metaHeaderEnabled = metadataEnabled;
        return this;
    }

    /**
     * Creates a new {@link RestClient} based on the provided configuration.
     */
    public RestClient build() {
        if (failureListener == null) {
            failureListener = new RestClient.FailureListener();
        }
        CloseableHttpAsyncClient httpClient = this.createHttpClient();
        RestClient restClient = new RestClient(
                httpClient,
                defaultHeaders,
                nodes,
                pathPrefix,
                failureListener,
                nodeSelector,
                strictDeprecationMode,
                compressionEnabled,
                metaHeaderEnabled
        );
        httpClient.start();
        return restClient;
    }

    /**
     * Similar to {@code org.apache.http.impl.nio.reactor.AbstractMultiworkerIOReactor.DefaultThreadFactory} but with better thread names.
     */
    private static class RestClientThreadFactory implements ThreadFactory {
        private static final AtomicLong CLIENT_THREAD_POOL_ID_GENERATOR = new AtomicLong();

        private final long clientThreadPoolId = CLIENT_THREAD_POOL_ID_GENERATOR.getAndIncrement(); // 0-based
        private final AtomicLong clientThreadId = new AtomicLong();

        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(
                    runnable,
                    String.format(Locale.ROOT, THREAD_NAME_FORMAT, clientThreadPoolId, clientThreadId.incrementAndGet()) // 1-based
            );
        }
    }

    private CloseableHttpAsyncClient createHttpClient() {
        // default timeouts are all infinite
        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom()
                .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MILLIS)
                .setSocketTimeout(DEFAULT_SOCKET_TIMEOUT_MILLIS);
        if (requestConfigCallback != null) {
            requestConfigBuilder = requestConfigCallback.customizeRequestConfig(requestConfigBuilder);
        }

        try {
            HttpAsyncClientBuilder httpAsyncClientBuilder = HttpAsyncClients.custom()
                    .setDefaultRequestConfig(requestConfigBuilder.build())
                    // default settings for connection pooling may be too constraining
                    .setMaxConnPerRoute(DEFAULT_MAX_CONN_PER_ROUTE)
                    .setMaxConnTotal(DEFAULT_MAX_CONN_TOTAL)
                    .setSSLContext(SSLContext.getDefault())
                    .setUserAgent(USER_AGENT_HEADER_VALUE)
                    .setTargetAuthenticationStrategy(new PersistentCredentialsAuthenticationStrategy());
            if (httpClientConfigCallback != null) {
                httpAsyncClientBuilder = httpClientConfigCallback.customizeHttpClient(httpAsyncClientBuilder);
            }

            // Build the base async client
            CloseableHttpAsyncClient baseAsyncClient = httpAsyncClientBuilder.build();

            // Wrap it with caching support
            CacheConfig cacheConfig = CacheConfig.custom()
                    .setMaxCacheEntries(1000)
                    .setMaxObjectSize(8192)
                    .build();

            return new CloseableHttpAsyncClient() {
                CachingHttpAsyncClient delegate = new CachingHttpAsyncClient(baseAsyncClient, cacheConfig);
                @Override
                public <T> Future<T> execute(HttpAsyncRequestProducer requestProducer, HttpAsyncResponseConsumer<T> responseConsumer, HttpContext context, FutureCallback<T> callback) {
                    throw new UnsupportedOperationException("Streaming and cached http requests is not supported");

                }

                @Override
                public Future<HttpResponse> execute(
                        final HttpHost target,
                        final HttpRequest originalRequest,
                        final HttpContext context,
                        final FutureCallback<HttpResponse> futureCallback) {
                    HttpCacheContext cacheContext = HttpCacheContext.adapt(context);
                    return delegate.execute(target, originalRequest, cacheContext, futureCallback);
                }

                @Override
                public void close() throws IOException {
                    baseAsyncClient.close();
                }

                @Override
                public boolean isRunning() {
                    return baseAsyncClient.isRunning();
                }

                @Override
                public void start() {
                    baseAsyncClient.start();
                }
            };
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("could not create the default ssl context", e);
        }
    }


    public interface RequestConfigCallback {
        /**
         * Allows to customize the {@link RequestConfig} that will be used with each request.
         * It is common to customize the different timeout values through this method without losing any other useful default
         * value that the {@link org.elasticsearch.client.RestClientBuilder} internally sets.
         */
        RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder requestConfigBuilder);
    }

    public interface HttpClientConfigCallback {
        /**
         * Allows to customize the {@link CloseableHttpAsyncClient} being created and used by the {@link RestClient}.
         * Commonly used to customize the default {@link org.apache.http.client.CredentialsProvider} for authentication
         * or the {@link SchemeIOSessionStrategy} for communication through ssl without losing any other useful default
         * value that the {@link org.elasticsearch.client.RestClientBuilder} internally sets, like connection pooling.
         */
        HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder);
    }
}
