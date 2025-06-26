// ElasticsearchConfig.java
package com.tenacy.logpulse.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.elc.AutoCloseableElasticsearchClient;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.tenacy.logpulse.elasticsearch.repository")
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.rest.uris}")
    private String elasticsearchUri;

    @Bean
    public RestClient restClient() {
        String[] hostAndPort = elasticsearchUri.split(":");
        String host = hostAndPort[0];
        int port = Integer.parseInt(hostAndPort[1]);

        return RestClient.builder(
                new HttpHost(host, port)
        ).build();
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