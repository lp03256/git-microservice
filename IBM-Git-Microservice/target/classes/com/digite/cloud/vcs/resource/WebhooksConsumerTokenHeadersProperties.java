package com.digite.cloud.vcs.resource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import java.util.Map;

@ConstructorBinding
@ConfigurationProperties(prefix = "digite.cloud.swiftalk.piglet.webhook")
public class WebhooksConsumerTokenHeadersProperties {

    private final Map<String, String> headers;

    public WebhooksConsumerTokenHeadersProperties ( Map<String, String> headers) {
        this.headers = headers;
    }

    public String getTokenHeaderName(String app) {
        return headers.get(app);
    }
}
