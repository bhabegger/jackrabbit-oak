/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.index.elastic;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonpDeserializer;
import co.elastic.clients.json.NamedDeserializer;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.json.stream.JsonParser;
import org.apache.http.HttpHost;
import org.apache.http.client.cache.CacheResponseStatus;
import org.apache.http.client.cache.HttpCacheContext;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClients;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class ElasticsearchAdhocCachingClient {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchAdhocCachingClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JacksonJsonpMapper JSONP_MAPPER = new JacksonJsonpMapper(MAPPER);
    private final CloseableHttpClient httpClient;
    private final URI endpoint;

    public ElasticsearchAdhocCachingClient(HttpHost httpHost, String apiToken) {
        CacheConfig cacheConfig = CacheConfig.custom()
                .setMaxCacheEntries(1000)
                .setMaxObjectSize(8192)
                .build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(30000)
                .setSocketTimeout(30000)
                .build();
        ArrayList<BasicHeader> defaultHeaders = new ArrayList<>();
        defaultHeaders.add(new BasicHeader("Content-Type", "application/json"));
        if (apiToken != null) {
            defaultHeaders.add(new BasicHeader("Authorization", "ApiKey " + apiToken));
        }
        try {
            this.endpoint = new URI(httpHost.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        this.httpClient = CachingHttpClients.custom()
                .setCacheConfig(cacheConfig)
                .setDefaultRequestConfig(requestConfig)
                .setDefaultHeaders(defaultHeaders)
                .build();

    }

    public CompletableFuture<SearchResponse<ObjectNode>> search(SearchRequest searchRequest, Class<ObjectNode> clazz) {
        try {
            String indices = String.join(",", searchRequest.index());
            String uri = endpoint.resolve(indices).resolve("_search").toString();
            HttpCacheContext context = HttpCacheContext.create();
            HttpPost httpRequest = new HttpPost(uri);
            httpRequest.setEntity(new StringEntity(MAPPER.writeValueAsString(searchRequest)));
            try (CloseableHttpResponse httpResponse = httpClient.execute(httpRequest, context)) {
                CacheResponseStatus responseStatus = context.getCacheResponseStatus();
                switch (responseStatus) {
                    case CACHE_HIT:
                        LOG.info("A response was generated from the cache with " +
                                "no requests sent upstream");
                        break;
                    case CACHE_MODULE_RESPONSE:
                        LOG.info("The response was generated directly by the " +
                                "caching module");
                        break;
                    case CACHE_MISS:
                        LOG.info("The response came from an upstream server");
                        break;
                    case VALIDATED:
                        LOG.info("The response was generated from the cache " +
                                "after validating the entry with the origin server");
                        break;
                }


                JsonpDeserializer<SearchResponse<ObjectNode>> deserializer = SearchResponse.createSearchResponseDeserializer(new NamedDeserializer<>("co.elastic.clients:Deserializer:_global.search.Response.TDocument"));
                var mapper = JSONP_MAPPER.withAttribute("co.elastic.clients:Deserializer:_global.search.Response.TDocument", JsonpDeserializer.of(ObjectNode.class));
                InputStream content = httpResponse.getEntity().getContent();
                JsonParser parser = mapper.jsonProvider().createParser(content);
                SearchResponse<ObjectNode> response = deserializer.deserialize(parser, mapper);
                return CompletableFuture.completedFuture(response);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
