package com.digite.cloud.vcs.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Delegate;

@AllArgsConstructor
@Getter
public class WebhookConfigResponse {

    private String url;
    @Delegate
    @JsonIgnore
    private WebhookConfig webhookConfig;
}