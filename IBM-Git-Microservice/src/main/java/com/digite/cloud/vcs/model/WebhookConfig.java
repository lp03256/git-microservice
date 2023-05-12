package com.digite.cloud.vcs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

@Getter
@Builder
@ToString
@Document(collection = "webhook_configs")
@NoArgsConstructor(force = true)
@AllArgsConstructor(onConstructor_ = {@PersistenceCreator})
@CompoundIndex(name = "webhook_idx", def = "{'realmId': 1, 'accountId': 1, 'projectId': 1, 'category': 1, 'app': 1}", unique = true)
@JsonIgnoreProperties({"id", "createdAt", "lastModifiedAt", "version"})
public class WebhookConfig implements Serializable {

    @Id
    private String id;

    @CreatedDate
    private Date createdAt;

    @LastModifiedDate
    private Date lastModifiedAt;

    @Version
    private int version;

    @Indexed(unique = true)
    private String webhookId;
    private String realmId;
    private String accountId;
    private String projectId;
    private String authorId;
    private String authorName;
    private String category;
    private String app;
    private String tokenHeaderValue;
    private String tokenHeaderName;

    public WebhookConfig updateWebhookToken(String token) {
        this.tokenHeaderValue = token;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebhookConfig that = (WebhookConfig) o;
        return getWebhookId().equals(that.getWebhookId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getWebhookId());
    }
}
