package com.petnose.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class EmbedClientConfig {

    @Value("${embed.url}")
    private String embedUrl;

    @Value("${embed.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${embed.response-timeout-ms:10000}")
    private long responseTimeoutMs;

    @Bean("embedWebClient")
    public WebClient embedWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs));

        return WebClient.builder()
                .baseUrl(embedUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
