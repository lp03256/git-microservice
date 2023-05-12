package com.digite.cloud.vcs;

import com.digite.cloud.vcs.mappings.MappingDataService;
import com.digite.cloud.vcs.model.*;
import com.digite.cloud.vcs.repository.GitRepository;
import com.digite.cloud.vcs.repository.WebhookConfigRepository;
import com.digite.cloud.vcs.resource.GitController;
import com.digite.cloud.vcs.resource.WebhooksConsumerFunctionConfiguration;
import com.digite.cloud.vcs.transformer.TransformerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.unit.DataSize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@Slf4j
@EnableIntegration
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
        "digite.cloud.allowed.payload.size=10KB",
        "spring.mongodb.embedded.version=3.5.5",
        "digite.cloud.rest.enabled=true"
})
@Import({ WebhooksConsumerFunctionConfiguration.class})
@ContextConfiguration(classes = { GitController.class, RequestValidator.class,
        TransformerService.class, MappingDataService.class})
@SpringJUnitConfig(classes = {TransformerService.class})
@DisplayName("Webhook Payload REST API")
class WebhookPayloadTests {
    public static final String WEBHOOKS_CONSUMERS_REST_URL = "/webhooks/v1/Ou1cqVGoiUy2je0DY";

    @Autowired
    TransformerService transformerService;

    @MockBean
    GitRepository gitRepository;

    @Value("${digite.cloud.allowed.payload.size}")
    public String allowedPayloadSize;

    @Autowired
    WebTestClient webClient;

    MultiValueMap<String, String> headers;

    @MockBean
    WebhookConfigRepository repository;

    @MockBean
    private MappingMongoConverter mappingMongoConverter;

    @MockBean
    StreamBridge streamBridge;

    @BeforeEach
    void setUp() {
        headers = new HttpHeaders();
        headers.setAll( new HashMap<String, String>() {{
            put("X-FABRIC-CORRELATION", "r1:a1:u1");
            put("X-OWNER-ID", "p1");
            put( HttpHeaders.CONTENT_TYPE,  MediaType.APPLICATION_JSON_VALUE);
        }});
    }

    @Test
    @DisplayName("SHOULD Return 413 If Payload Size exceeds certain threshold")
    void testPayload() throws IOException {
        WebhookRequest request = new WebhookRequest("vcs", "github", "Lalit Patil");
        BDDMockito.when(repository.findByWebhookId(anyString())).thenReturn( Mono.just( WebhookConfig.builder()
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
                .build()));

        BDDMockito.when( streamBridge.send( anyString(), anyString(), any(Object.class) ) ).thenReturn( true );

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> payload = objectMapper.readValue(
                new String( Files.readAllBytes( Paths.get(new ClassPathResource("github-payload.json" ).getFile().getAbsolutePath()))), Map.class);

        int originalPayloadSize = payload.size();

        Long allowedPayloadLimit = DataSize.parse(allowedPayloadSize).toBytes();

        BDDMockito.when(gitRepository.save(any( Git.class))).thenReturn( Mono.just( Git.builder()
                .accountId("a1")
                .ownerId("p1")
                .realmId("r1")
                .gitId("G12345678")
                .commitMessage( "UST1234 Commited")
                .commitTime( "18:02:25+UTC05:30" )
                .changes( new Changes(new ArrayList<>().toArray( new String[0] ),new ArrayList<>().toArray( new String[0] ),
                        new ArrayList<>().toArray( new String[0] ) ) )
                .repo( new Repo( "cowin", "http://cowin.gihub.com", "") )
                .commitBy( new Commitby( "lpatil", "" ) )
                .branch( "main" )
                .after( "" )
                .before( "" )
                .compare( "" )
                .build() ));


        webClient.post()
                .uri( URI.create( WEBHOOKS_CONSUMERS_REST_URL ) )
                .contentType( MediaType.APPLICATION_JSON )
                .headers( httpHeaders -> httpHeaders.addAll( headers ) )
                .bodyValue( payload )
                .exchange()
                .expectStatus().isEqualTo(originalPayloadSize > allowedPayloadLimit.intValue() ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.OK );
    }

    @Test
    @DisplayName("SHOULD Return 200 if Git Endpoints are working")
    void testGitRestAPisWhenPropertyTrue() throws IOException {
        WebhookRequest request = new WebhookRequest("vcs", "github", "Lalit Patil");
        BDDMockito.when(repository.findByWebhookId(anyString())).thenReturn( Mono.just(WebhookConfig.builder()
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
                .build()));

        BDDMockito.when( streamBridge.send( anyString(), anyString(), any(Object.class) ) ).thenReturn( true );

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> payload = objectMapper.readValue(
                new String( Files.readAllBytes( Paths.get(new ClassPathResource("github-payload.json" ).getFile().getAbsolutePath()))), Map.class);

        BDDMockito.when(gitRepository.save(any(Git.class))).thenReturn( Mono.just( Git.builder()
                .accountId("a1")
                .ownerId("p1")
                .realmId("r1")
                .gitId("G12345678")
                .commitMessage( "UST1234 Commited")
                .commitTime( "18:02:25+UTC05:30" )
                .changes( new Changes(new ArrayList<>().toArray( new String[0] ),new ArrayList<>().toArray( new String[0] ),
                        new ArrayList<>().toArray( new String[0] ) ) )
                .repo( new Repo( "cowin", "http://cowin.gihub.com", "") )
                .commitBy( new Commitby( "lpatil", "" ) )
                .branch( "main" )
                .after( "" )
                .before( "" )
                .compare( "" )
                .build() ));


        webClient.post()
                .uri( URI.create( WEBHOOKS_CONSUMERS_REST_URL ) )
                .contentType( MediaType.APPLICATION_JSON )
                .headers( httpHeaders -> httpHeaders.addAll( headers ) )
                .bodyValue( payload )
                .exchange()
                .expectStatus().isEqualTo( HttpStatus.OK );

       Mockito.when(gitRepository.findByRealmIdAndAccountIdAndOwnerIdAndItemCode(
                anyString(), anyString(), anyString(), anyString())).thenReturn( Flux.just(Git.builder()
                .accountId("a1")
                .ownerId("p1")
                .realmId("r1")
                .gitId("G12345678")
                .commitMessage( "UST1234 Commited")
                .commitTime( "18:02:25+UTC05:30" )
                .changes( new Changes(new ArrayList<>().toArray( new String[0] ),new ArrayList<>().toArray( new String[0] ),
                        new ArrayList<>().toArray( new String[0] ) ) )
                .repo( new Repo( "cowin", "http://cowin.gihub.com", "") )
                .commitBy( new Commitby( "lpatil", "" ) )
                .branch( "main" )
                .after( "" )
                .before( "" )
                .compare( "" )
                .build()));

        webClient.get()
                .uri( URI.create("/rest/v1/git/UST1234"))
                .headers( httpHeaders -> httpHeaders.addAll(headers))
                .exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("SHOULD Return 200 if Gitlab is successful")
    void testGitLabPayload() throws IOException {
        WebhookRequest request = new WebhookRequest("vcs", "gitlab", "Lalit Patil");
        BDDMockito.when(repository.findByWebhookId(anyString())).thenReturn( Mono.just( WebhookConfig.builder()
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
                .build()));

        BDDMockito.when( streamBridge.send( anyString(), anyString(), any(Object.class) ) ).thenReturn( true );

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> payload = objectMapper.readValue(
                new String( Files.readAllBytes( Paths.get(new ClassPathResource("gitlab-payload.json" ).getFile().getAbsolutePath()))), Map.class);

        BDDMockito.when(gitRepository.save(any( Git.class))).thenReturn( Mono.just( Git.builder()
                .accountId("a1")
                .ownerId("p1")
                .realmId("r1")
                .gitId("G12345678")
                .commitMessage( "UST1234 Commited")
                .commitTime( "18:02:25+UTC05:30" )
                .changes( new Changes(new ArrayList<>().toArray( new String[0] ),new ArrayList<>().toArray( new String[0] ),
                        new ArrayList<>().toArray( new String[0] ) ) )
                .repo( new Repo( "cowin", "http://cowin.gihub.com", "") )
                .commitBy( new Commitby( "lpatil", "" ) )
                .branch( "main" )
                .after( "" )
                .before( "" )
                .compare( "" )
                .build() ));


        webClient.post()
                .uri( URI.create( WEBHOOKS_CONSUMERS_REST_URL ) )
                .contentType( MediaType.APPLICATION_JSON )
                .headers( httpHeaders -> httpHeaders.addAll( headers ) )
                .bodyValue( payload )
                .exchange()
                .expectStatus().isEqualTo( HttpStatus.OK );

        Mockito.when(gitRepository.findByRealmIdAndAccountIdAndOwnerIdAndItemCode(
                anyString(), anyString(), anyString(), anyString())).thenReturn( Flux.just(Git.builder()
                .accountId("a1")
                .ownerId("p1")
                .realmId("r1")
                .gitId("G12345678")
                .commitMessage( "UST1234 Commited")
                .commitTime( "18:02:25+UTC05:30" )
                .changes( new Changes(new ArrayList<>().toArray( new String[0] ),new ArrayList<>().toArray( new String[0] ),
                        new ArrayList<>().toArray( new String[0] ) ) )
                .repo( new Repo( "cowin", "http://cowin.gihub.com", "") )
                .commitBy( new Commitby( "lpatil", "" ) )
                .branch( "main" )
                .after( "" )
                .before( "" )
                .compare( "" )
                .build()));

        webClient.get()
                .uri( URI.create("/rest/v1/git/UST1234"))
                .headers( httpHeaders -> httpHeaders.addAll(headers))
                .exchange().expectStatus().isOk();
    }
}
