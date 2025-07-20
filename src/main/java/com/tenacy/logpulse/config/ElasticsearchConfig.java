package com.tenacy.logpulse.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.elc.AutoCloseableElasticsearchClient;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import java.net.URI;
import java.net.URISyntaxException;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.tenacy.logpulse.elasticsearch.repository")
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.uris:http://elasticsearch:9200}")
    private String elasticsearchUri;

    @Value("${spring.elasticsearch.socket-timeout:30000}")
    private int socketTimeout;

    @Value("${spring.elasticsearch.connection-timeout:5000}")
    private int connectTimeout;

    @Bean
    public RestClient restClient() {
        try {
            // URI를 적절히 파싱
            URI uri = new URI(elasticsearchUri);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 9200;
            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";

            RestClientBuilder builder = RestClient.builder(
                    new HttpHost(host, port, scheme)
            );

            // 타임아웃 설정
            builder.setRequestConfigCallback(
                    (RequestConfig.Builder requestConfigBuilder) -> requestConfigBuilder
                            .setSocketTimeout(socketTimeout)
                            .setConnectTimeout(connectTimeout)
            );

            // 대량 처리를 위한 커넥션 풀 설정
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setMaxConnTotal(100)
                            .setMaxConnPerRoute(30));

            return builder.build();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid Elasticsearch URI: " + elasticsearchUri, e);
        }
    }

    @Bean
    public ElasticsearchTransport elasticsearchTransport() {
        return new RestClientTransport(
                restClient(),
                new JacksonJsonpMapper()
        );
    }

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        return new ElasticsearchClient(elasticsearchTransport());
    }

    @Bean
    public ElasticsearchTemplate elasticsearchTemplate() {
        SimpleElasticsearchMappingContext mappingContext = new SimpleElasticsearchMappingContext();
        ElasticsearchConverter converter = new MappingElasticsearchConverter(mappingContext);

        return new ElasticsearchTemplate(
                new AutoCloseableElasticsearchClient(elasticsearchTransport()),
                converter
        );
    }
}