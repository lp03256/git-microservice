package com.digite.cloud.vcs.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;

import javax.validation.constraints.NotEmpty;
import java.io.Serializable;

@Getter
@ToString
@Builder
@AllArgsConstructor
@EqualsAndHashCode
@JsonDeserialize(builder = WebhookRequest.WebhookRequestBuilder.class)
public class WebhookRequest implements Serializable {

    @JsonProperty
    @NotEmpty(message = "category name is mandatory")
    private String category;
    @JsonProperty
    @NotEmpty(message = "app name is mandatory")
    private String app;
    @JsonProperty
    @NotEmpty(message = "author name is mandatory")
    private String authorName;

}
