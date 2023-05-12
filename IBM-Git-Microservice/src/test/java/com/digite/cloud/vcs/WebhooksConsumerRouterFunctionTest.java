package com.digite.cloud.vcs;

import com.digite.cloud.vcs.model.RequestValidator;
import com.digite.cloud.vcs.model.WebhookConfig;
import com.digite.cloud.vcs.model.WebhookRequest;
import com.digite.cloud.vcs.repository.WebhookConfigRepository;
import com.digite.cloud.vcs.resource.WebhookConsumerRestApi;
import com.digite.cloud.vcs.resource.WebhooksConsumerTokenHeadersProperties;
import com.github.javafaker.Faker;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@Slf4j
@WebFluxTest(properties = {
        "spring.cloud.vault.enabled=false",
        "spring.main.banner-mode=off",
        "spring.cloud.bus.enabled=false",
        "spring.cloud.config.enabled=false",
        "logging.level.root=DEBUG",
        "server.error.include-message=always",
        "spring.application.name=piglet",
        "digite.cloud.fabric.headers.fabric-auth=X-FABRIC-CORRELATION",
        "digite.cloud.fabric.headers.owner-id=X-OWNER-ID",
        "digite.cloud.rest.enabled=true"
})
@ContextConfiguration(classes = { WebhookConsumerRestApi.class, RequestValidator.class})
@DisplayName("Webhook Config REST API")
class WebhooksConsumerRouterFunctionTest {

    public static final String WEBHOOKS_CONSUMERS_REST_URL = "/rest/v1/webhooks/consumers";

    @Autowired
    WebTestClient webClient;

    MultiValueMap<String, String> headers;

    @MockBean
    WebhookConfigRepository repository;

    @MockBean
    WebhooksConsumerTokenHeadersProperties headersProperties;

    @BeforeEach
    void setUp() {
        headers = new HttpHeaders();
        headers.setAll(new HashMap<String, String>() {{
            put("X-FABRIC-CORRELATION", "r1:a1:u1");
            put("X-OWNER-ID", "p1");
            put( HttpHeaders.CONTENT_TYPE,  MediaType.APPLICATION_JSON_VALUE);
        }});
    }

    @Test
    @DisplayName("SHOULD respond with push url when webhook is registered successfully")
    void testGeneratesWebhookId() {
        WebhookRequest request = new WebhookRequest("vcs", "gitlab", "Tera Baap");
        BDDMockito.when(repository.save(ArgumentMatchers.any( WebhookConfig.class)))
                .thenReturn(
                        Mono.just(WebhookConfig.builder()
                                .app(request.getApp())
                                .accountId("a1")
                                .authorId("u1")
                                .authorName("User Name")
                                .category(request.getCategory())
                                .createdAt(Date.from(Instant.now()))
                                .id(String.valueOf(new SecureRandom().nextLong()))
                                .projectId(headers.getFirst("X-OWNER-ID"))
                                .realmId(Objects.requireNonNull(headers.getFirst("X-FABRIC-CORRELATION")).split(":")[0])
                                .version(0)
                                .webhookId("Ou1cqVGoiUy2je0DY")
                                .tokenHeaderName("X-Some-Token")
                                .tokenHeaderValue("iukMy12ooUZWf5QPm")
                                .build()
                        ));
        BDDMockito.when(headersProperties.getTokenHeaderName(anyString())).thenReturn("X-Gitlab-Token");
        webClient.post()
                .uri(URI.create(WEBHOOKS_CONSUMERS_REST_URL))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.webhook", IsEqual.equalTo("http://localhost:8080/webhooks/v1/Ou1cqVGoiUy2je0DY"));
    }

    @Test
    @DisplayName("SHOULD not allow saving empty webhook configuration")
    void testDoesNotSaveEmptyBody() {
        webClient.post()
                .uri(URI.create(WEBHOOKS_CONSUMERS_REST_URL))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("cannot register webhook with empty body");
    }

    @Test
    @DisplayName("SHOULD Fail saving webhook config if any of the mandatory fields are missing")
    void testFailsWhenMandatoryFieldsMissing() {

        WebhookRequest request = WebhookRequest.builder()
                .category("vcs")
                .authorName("Yeda Developer").build();

        webClient.post()
                .uri(URI.create(WEBHOOKS_CONSUMERS_REST_URL))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .bodyValue(request)
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("app: app name is mandatory");
    }

    @Test
    @DisplayName("SHOULD assign page 0 and size 10 as default paging options")
    void testDefaultPageSizIsTen() {
        Faker faker = new Faker(new Locale("en-IND"));
        List<WebhookConfig> configs = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            WebhookConfig config = WebhookConfig.builder()
                    .accountId("a1")
                    .authorName(faker.name().fullName())
                    .app(faker.app().name())
                    .tokenHeaderValue(faker.random().hex(15))
                    .tokenHeaderName("X-Token")
                    .authorId("u1")
                    .projectId("p1")
                    .realmId("r1")
                    .webhookId(faker.random().hex(15))
                    .category("vcs")
                    .build();
            configs.add(config);
        }
        Mockito.when(repository.findByRealmIdAndAccountIdAndProjectId(anyString(), anyString(), anyString(), any(Pageable.class)))
                .thenReturn(Flux.fromIterable(configs));
        WebTestClient.ResponseSpec response = webClient.get().uri(URI.create(WEBHOOKS_CONSUMERS_REST_URL))
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .exchange();
        response.expectStatus().isOk();

        Mockito.verify(repository, Mockito.times(1))
                .findByRealmIdAndAccountIdAndProjectId("r1", "a1", "p1", PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("SHOULD not allow a request to fetch more than 20 items")
    void testRejectsPageSizeOfMoreThanTwenty() {

        webClient.get().uri(URI.create(WEBHOOKS_CONSUMERS_REST_URL + "?page=0&size=21")).headers(httpHeaders -> httpHeaders.addAll(headers))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("cannot process page size of more than 20");
    }

    @Test
    @DisplayName("SHOULD return 200 if webhook is found")
    void testGetAllWebhooks() {
        BDDMockito.when(repository.findByRealmIdAndAccountIdAndProjectId(anyString(),
                anyString(), anyString(), any(Pageable.class))).thenReturn(Flux.just(WebhookConfig.builder()
                .app("github")
                .accountId("a1")
                .authorId("u1")
                .authorName("User Name")
                .category("vcs")
                .createdAt(Date.from(Instant.now()))
                .id(String.valueOf(new SecureRandom().nextLong()))
                .projectId(headers.getFirst("X-OWNER-ID"))
                .realmId(Objects.requireNonNull(headers.getFirst("X-FABRIC-CORRELATION")).split(":")[0])
                .version(0)
                .webhookId("Ou1cqVGoiUy2je0DY")
                .tokenHeaderName("X-Some-Token")
                .tokenHeaderValue("iukMy12ooUZWf5QPm")
                .build()
        ));
        webClient.get().uri(URI.create(WEBHOOKS_CONSUMERS_REST_URL))
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("SHOULD not allow delete request with empty webhookId")
    void testRejectsDeleteWithoutWebhookId() {

        webClient.delete().uri(URI.create(WEBHOOKS_CONSUMERS_REST_URL)).headers(httpHeaders -> httpHeaders.addAll(headers))
                .exchange().expectStatus().isNotFound();
    }

    @Test
    @DisplayName("SHOULD fail delete when webhookId not found")
    void testRejectsDeleteWhenWebhookIdNotFound() {
        BDDMockito.when(repository.findByWebhookId(anyString())).thenReturn(Mono.empty());
        webClient.delete().uri(URI.create(WEBHOOKS_CONSUMERS_REST_URL + "/W-987654")).headers(httpHeaders -> httpHeaders.addAll(headers))
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.message").value(Matchers.endsWith(" not found"));
    }

    @Test
    @DisplayName("SHOULD allows updating only token header value")
    void testRejectsUpdateWithoutWebhookId() {

        BDDMockito.when(repository.findByWebhookId(anyString())).thenReturn(Mono.just(WebhookConfig.builder()
                .webhookId("W-987654")
                .realmId("r1")
                .accountId("a1")
                .projectId("p1")
                .authorId("u1")
                .authorName("Sabka Bhai")
                .category("vcs")
                .app("gitlab")
                .tokenHeaderName("X-GitlabToken")
                .tokenHeaderValue("DvY5lS16BjTaKUHTBiz9mlx9NjxbwKu99yRqH9yis")
                .build()));
        webClient.put().uri(URI.create(WEBHOOKS_CONSUMERS_REST_URL + "/W-987654")).headers(httpHeaders -> httpHeaders.addAll(headers))
                .bodyValue(new HashMap<String, String>() {{
                               put("category", "foo");
                           }})
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.webhookId").isEqualTo("W-987654")
                .jsonPath("$.tokenHeaderValue").value(Matchers.not("DvY5lS16BjTaKUHTBiz9mlx9NjxbwKu99yRqH9yis"));
    }

    @Test
    @DisplayName("SHOULD not allow upsert operation")
    void testNoUpsert() {
        BDDMockito.when(repository.findByWebhookId(anyString())).thenReturn(Mono.empty());
        webClient.put().uri(URI.create(WEBHOOKS_CONSUMERS_REST_URL + "/W-987654")).headers(httpHeaders -> httpHeaders.addAll(headers))
                .bodyValue(new HashMap<String, String>() {{
                               put("token", "foo");
                           }})
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.message").value(Matchers.endsWith(" not found"));
    }

}
