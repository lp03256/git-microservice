package com.digite.cloud.vcs;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@EmbeddedKafka(
        brokerProperties = {"listeners=PLAINTEXT://localhost:29092"},
        topics = {"vcs-github-dev"},
        partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "spring.main.banner-mode=off",
        "spring.cloud.vault.enabled=false",
        "logging.level.root=DEBUG",
        "spring.mongodb.embedded.version=3.4.6",
        "server.error.include-message=always",
        "digite.cloud.fabric.headers.fabric-auth=X-FABRIC-CORRELATION",
        "digite.cloud.fabric.headers.owner-id=X-OWNER-ID",
        "spring.cloud.stream.kafka.binder.brokers=localhost:29092",
        "spring.data.mongodb.database=minions_db",
        "spring.data.mongodb.port=27017",
        "spring.data.mongodb.host=localhost",
        "server.port=8081",
        "management.endpoint.health.probes.enabled=true",
        "management.health.livenessState.enabled=true",
        "management.health.readinessState.enabled=true",
        "spring.cloud.stream.function.definition=handle",
        "spring.cloud.stream.bindings.handle-in-0.destination=vcs-github-dev",
        "spring.cloud.stream.bindings.handle-in-0.group=fabric-git-consumers-dev",
        "spring.data.mongodb.auto-index-creation=true",
       "spring.cloud.stream.kafka.default.producer.configuration.key.serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.cloud.stream.kafka.default.producer.configuration.value.serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.cloud.stream.kafka.default.producer.configuration.max.block.ms=100",
        "spring.cloud.stream.kafka.default.producer.configuration.spring.json.add.type.headers=false",
        "spring.cloud.stream.kafka.default.consumer.configuration.key.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.cloud.stream.kafka.default.consumer.configuration.value.deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.cloud.stream.kafka.default.consumer.configuration.spring.json.value.default.type=java.util.LinkedHashMap",
        "spring.cloud.stream.binder.autoCreateTopics=false",
        "spring.cloud.stream.default.producer.useNativeEncoding=true",
        "spring.cloud.stream.default.consumer.useNativeDecoding=true"
})
class GitKafkaTest {

    @Autowired
    WebTestClient webClient;
    @Autowired
    ReactiveMongoOperations mongoOperations;
    @Value("${spring.cloud.stream.bindings.handle-in-0.destination}")
    String handlerInTopic;
    @Autowired
    StreamBridge streamBridge;

    @Test
    void githubTest() {
        MultiValueMap multiValueMap = new LinkedMultiValueMap<>();
        multiValueMap.add( "commits",  Map.of("message","UST1234 Updated server.js",
                            "timestamp","09:08:12+05:30UTC",
                            "added","",
                            "modified","k.java",
                            "removed","",
                            "committer",Map.of("name","")) );
        multiValueMap.add( "commits",  Map.of("message","UST1234 Updated db.js",
                "timestamp","09:08:12+05:30UTC",
                "added","abc.js",
                "modified","",
                "removed","",
                "committer",Map.of("name","")));

        Message<?> message = MessageBuilder.withPayload(
                    Map.of("event",
                                Map.of("itemType", "UST",
                                        "eventName", "modifyEForm",
                                        "realmId", "r1",
                                        "accountId", "a1",
                                        "app", "github",
                                        "ownerId", "p1"),
                            "content",
                                Map.of("repository", Map.of("default_branch","main",
                                                                "name","cowin-lp-backend",
                                                                "url","https://github.com/cowin-lp-backend",
                                                                "description",""),
                                        "commits",
                                                multiValueMap.get( "commits" ),
                                        "before","as23erfes",
                                        "after","1aqsdddv",
                                        "pusher",Map.of("name","lpatil"),
                                        "compare","https://github.com/cowin-lp-backend/diff",
                                        "sender",Map.of("avatar_url",""))))
                .setHeader( KafkaHeaders.MESSAGE_KEY, "r1:a1")
                .setHeader(KafkaHeaders.TOPIC, handlerInTopic)
                .build();
        boolean sent = streamBridge.send(handlerInTopic, "kafka", message);
        await().until(() -> sent);
        log.debug("Published Kafka Message at offset topic {}", handlerInTopic);
        await().until(() -> mongoOperations.exists(Query.query(Criteria.where("itemCode").exists(true)), "git_data").block( Duration.ofMillis(10000)));
        Assertions.assertTrue(mongoOperations.exists(Query.query(Criteria.where("itemCode").is("UST1234")), "git_data").block(), "ItemCode successfully saved UST1234");

        HttpHeaders headers = new HttpHeaders();
        headers.setAll(Map.of("X-FABRIC-CORRELATION", "r1:a1:u1", "X-OWNER-ID", "p1"));

        webClient.get().uri( URI.create("http://localhost:8081/rest/v1/git/UST1234")).headers( httpHeaders -> httpHeaders.addAll(headers))
                .exchange().expectStatus().isOk();

    }

    @Test
    void gitlabTest() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> gitLabContent = objectMapper.readValue(
                new String( Files.readAllBytes( Paths.get(new ClassPathResource("gitlab-payload.json" ).getFile().getAbsolutePath()))), Map.class);

        Message<?> message = MessageBuilder.withPayload(
                        Map.of("event",
                                Map.of("itemType", "UST",
                                        "eventName", "modifyEForm",
                                        "realmId", "r1",
                                        "accountId", "a1",
                                        "app", "gitlab",
                                        "ownerId", "p1"),
                                "content", gitLabContent))
                .setHeader( KafkaHeaders.MESSAGE_KEY, "r1:a1")
                .setHeader(KafkaHeaders.TOPIC, "vcs-gitlab-dev")
                .build();
        boolean sent = streamBridge.send(handlerInTopic, "kafka", message);
        await().until(() -> sent);
        log.debug("Published Kafka Message at offset topic {}", "vcs-gitlab-dev");
        await().until(() -> mongoOperations.exists(Query.query(Criteria.where("itemCode").exists(true)), "git_data").block( Duration.ofMillis(10000)));
        Assertions.assertTrue(mongoOperations.exists(Query.query(Criteria.where("itemCode").is("DEF3")), "git_data").block(), "ItemCode successfully saved DEF3");

        HttpHeaders headers = new HttpHeaders();
        headers.setAll(Map.of("X-FABRIC-CORRELATION", "r1:a1:u1", "X-OWNER-ID", "p1"));

        webClient.get().uri( URI.create("http://localhost:8081/rest/v1/git/DEF3")).headers( httpHeaders -> httpHeaders.addAll(headers))
                .exchange().expectStatus().isOk();

    }
}
