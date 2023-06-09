// Generated by delombok at Fri Mar 17 17:12:13 IST 2023
package com.digite.cloud.vcs.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import javax.validation.constraints.NotEmpty;
//import java.io.Serial;
import java.io.Serializable;

@JsonDeserialize(builder = WebhookRequest.WebhookRequestBuilder.class)
public class WebhookRequest implements Serializable {
    /*@Serial
    private static final long serialVersionUID = 7119377917168939245L;*/
    @JsonProperty
    @NotEmpty(message = "category name is mandatory")
    private String category;
    @JsonProperty
    @NotEmpty(message = "app name is mandatory")
    private String app;
    @JsonProperty
    @NotEmpty(message = "author name is mandatory")
    private String authorName;


    @java.lang.SuppressWarnings("all")
    public static class WebhookRequestBuilder {
        @java.lang.SuppressWarnings("all")
        private String category;
        @java.lang.SuppressWarnings("all")
        private String app;
        @java.lang.SuppressWarnings("all")
        private String authorName;

        @java.lang.SuppressWarnings("all")
        WebhookRequestBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @JsonProperty
        @java.lang.SuppressWarnings("all")
        public WebhookRequest.WebhookRequestBuilder category(final String category) {
            this.category = category;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @JsonProperty
        @java.lang.SuppressWarnings("all")
        public WebhookRequest.WebhookRequestBuilder app(final String app) {
            this.app = app;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @JsonProperty
        @java.lang.SuppressWarnings("all")
        public WebhookRequest.WebhookRequestBuilder authorName(final String authorName) {
            this.authorName = authorName;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public WebhookRequest build() {
            return new WebhookRequest(this.category, this.app, this.authorName);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "WebhookRequest.WebhookRequestBuilder(category=" + this.category + ", app=" + this.app + ", authorName=" + this.authorName + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static WebhookRequest.WebhookRequestBuilder builder() {
        return new WebhookRequest.WebhookRequestBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public String getCategory() {
        return this.category;
    }

    @java.lang.SuppressWarnings("all")
    public String getApp() {
        return this.app;
    }

    @java.lang.SuppressWarnings("all")
    public String getAuthorName() {
        return this.authorName;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "WebhookRequest(category=" + this.getCategory() + ", app=" + this.getApp() + ", authorName=" + this.getAuthorName() + ")";
    }

    @java.lang.SuppressWarnings("all")
    public WebhookRequest(final String category, final String app, final String authorName) {
        this.category = category;
        this.app = app;
        this.authorName = authorName;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof WebhookRequest)) return false;
        final WebhookRequest other = (WebhookRequest) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$category = this.getCategory();
        final java.lang.Object other$category = other.getCategory();
        if (this$category == null ? other$category != null : !this$category.equals(other$category)) return false;
        final java.lang.Object this$app = this.getApp();
        final java.lang.Object other$app = other.getApp();
        if (this$app == null ? other$app != null : !this$app.equals(other$app)) return false;
        final java.lang.Object this$authorName = this.getAuthorName();
        final java.lang.Object other$authorName = other.getAuthorName();
        if (this$authorName == null ? other$authorName != null : !this$authorName.equals(other$authorName)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof WebhookRequest;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $category = this.getCategory();
        result = result * PRIME + ($category == null ? 43 : $category.hashCode());
        final java.lang.Object $app = this.getApp();
        result = result * PRIME + ($app == null ? 43 : $app.hashCode());
        final java.lang.Object $authorName = this.getAuthorName();
        result = result * PRIME + ($authorName == null ? 43 : $authorName.hashCode());
        return result;
    }
}
