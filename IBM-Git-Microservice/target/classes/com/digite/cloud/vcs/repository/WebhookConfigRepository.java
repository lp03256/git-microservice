package com.digite.cloud.vcs.repository;

import com.digite.cloud.vcs.model.WebhookConfig;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface WebhookConfigRepository extends ReactiveMongoRepository<WebhookConfig, String> {

    Mono<WebhookConfig> findByWebhookId(String webhookId);

    Flux<WebhookConfig> findByRealmIdAndAccountIdAndProjectId(String realmId, String accountId, String projectId, Pageable pageable);
}
