package com.digite.cloud.vcs.resource;

import com.digite.cloud.vcs.model.RequestValidator;
import com.digite.cloud.vcs.model.WebhookConfig;
import com.digite.cloud.vcs.model.WebhookConfigResponse;
import com.digite.cloud.vcs.model.WebhookRequest;
import com.digite.cloud.vcs.repository.WebhookConfigRepository;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.logging.Level.FINE;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

@Slf4j
@Configuration
@ConditionalOnExpression("${digite.cloud.rest.enabled:false}")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class WebhookConsumerRestApi {

    public static final String NOT_FOUND_SUFFIX = " not found";
    public static final String TOKEN_KEY_NAME = "token";
    private final SecureRandom random = new SecureRandom();

    private final RequestValidator requestValidator;
    private final WebhooksConsumerTokenHeadersProperties headersProperties;
    private final WebhookConfigRepository repository;

    @Setter(onMethod_ = @Value("${digite.cloud.swiftalk.piglet.rest-url:/rest/v1/webhooks/consumers}"))
    private String restUrl;
    @Setter(onMethod_ = @Value("${digite.cloud.swiftalk.piglet.webhook-id:webhookId}"))
    private String webhookIdPathVar;
    @Setter(onMethod_ = @Value("${digite.cloud.swiftalk.piglet.api-version:v1}"))
    private String currentVersion;
    @Setter(onMethod_ = @Value("${digite.cloud.fabric.headers.fabric-auth}"))
    private String fabricAuthHeader;
    @Setter(onMethod_ = @Value("${digite.cloud.fabric.headers.owner-id:X-OWNER-ID}"))
    private String ownerIdHeader;
    @Setter(onMethod_ = @Value("${digite.cloud.swiftalk.piglet.published-host}"))
    public String publishedHost;

    @SuppressWarnings("ConstantConditions")
    @Bean
    public RouterFunction<ServerResponse> webhooksApi() {
        String idRestUrl = restUrl + "/{" + webhookIdPathVar + "}";
        return route().POST(restUrl, accept(APPLICATION_JSON), serverRequest -> serverRequest.bodyToMono( WebhookRequest.class)
                                .switchIfEmpty(Mono.error(new ServerWebInputException("cannot register webhook with empty body")))
                                .doOnNext(requestValidator::validate)
                                .log(this.getClass().getName(), FINE)
                                .flatMap(payload -> {
                                    try {
                                        log.debug("{}Registering new webhook {}", serverRequest.exchange().getLogPrefix(), payload);
                                        String webhookId = generateWebhookId();
                                        return repository.save( WebhookConfig.builder()
                                                .category(payload.getCategory())
                                                .app(payload.getApp())
                                                .realmId(serverRequest.headers().firstHeader(fabricAuthHeader).split(":")[0])
                                                .accountId(serverRequest.headers().firstHeader(fabricAuthHeader).split(":")[1])
                                                .projectId(serverRequest.headers().firstHeader(ownerIdHeader))
                                                .authorId(serverRequest.headers().firstHeader(fabricAuthHeader).split(":")[2])
                                                .authorName(payload.getAuthorName())
                                                .webhookId(webhookId)
                                                .tokenHeaderName(headersProperties.getTokenHeaderName(payload.getApp()))
                                                .tokenHeaderValue(generateToken(String.join("", serverRequest.headers().firstHeader(fabricAuthHeader).split(":"))))
                                                .build());
                                    } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException |
                                             InvalidAlgorithmParameterException | InvalidKeyException |
                                             IllegalBlockSizeException |
                                             BadPaddingException e) {
                                        log.error("{}Failed generating webhook due to error", serverRequest.exchange().getLogPrefix(), e);
                                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "failed creating webhook, retry"));
                                    }
                                }).flatMap(next -> ServerResponse.status(HttpStatus.CREATED)
                                        .bodyValue(new HashMap<String, String>() {{
                                            put("webhook", publishedHost + "/" + String.join("/", "webhooks", currentVersion, next.getWebhookId()));
                                            put(TOKEN_KEY_NAME, next.getTokenHeaderValue());
                                        }}))
                                .doOnError(throwable -> log.error("{}Failed creating webhook sue to error {}", serverRequest.exchange().getLogPrefix(), throwable.getMessage(), throwable)), docs -> {
                            created201Response.andThen(xFabricCorrelationDocs).andThen(badRequestBodyResponse).andThen(cryptoAlgoFailedResponse).accept(docs);
                            docs.operationId("createWebhook");
                        }
                ).build()
                .and(route().PUT(idRestUrl, contentType(APPLICATION_JSON), serverRequest -> repository.findByWebhookId(serverRequest.pathVariable(webhookIdPathVar))
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "webhook not found")))
                        .log(this.getClass().getName(), FINE)
                        .map(webhookConfig -> {
                            try {
                                return webhookConfig.updateWebhookToken(generateToken(String.join("", serverRequest.headers().firstHeader(fabricAuthHeader).split(":"))));
                            } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException |
                                     InvalidAlgorithmParameterException | InvalidKeyException |
                                     IllegalBlockSizeException |
                                     BadPaddingException e) {
                                log.error("{}Failed generating webhook due to error", serverRequest.exchange().getLogPrefix(), e);
                                return Mono.error(new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "failed updating webhook, retry"));
                            }
                        })
                        .flatMap(webhookConfig -> ServerResponse.ok().bodyValue(webhookConfig))
                        .doOnError(throwable -> log.error("{}Failed refreshing webhook token due to error {}", serverRequest.exchange().getLogPrefix(), throwable.getMessage(), throwable)), docs -> {
                    docs.parameter(org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder().in(ParameterIn.PATH).name(webhookIdPathVar))
                            .response(org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder().responseCode("200").implementation(WebhookConfig.class));
                    xFabricCorrelationDocs.andThen(notFound404Response).andThen(cryptoAlgoFailedResponse).accept(docs);
                    docs.operationId("updateWebhook");
                }).build())
                .and(route().GET(restUrl, contentType(APPLICATION_JSON), serverRequest ->
                        repository.findByRealmIdAndAccountIdAndProjectId(serverRequest.headers().firstHeader(fabricAuthHeader).split(":")[0],
                                        serverRequest.headers().firstHeader(fabricAuthHeader).split(":")[1],
                                        serverRequest.headers().firstHeader(ownerIdHeader),
                                        PageRequest.of(Integer.parseInt(serverRequest.queryParam("page").orElse("0")), Integer.parseInt(serverRequest.queryParam("size").orElse("10"))))
                                .log(this.getClass().getName(), FINE)
                                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "no webhooks configured for this project")))
                                .filter(webhookConfig -> {
                                    if (serverRequest.queryParam("filter").isPresent())
                                        return serverRequest.queryParam("filter").get().split(":")[1].equals(webhookConfig.getApp());
                                    else
                                        return true;
                                })
                                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "no webhooks configured for this project")))
                                .collectList()
                                .flatMap(results -> {
                                    List<WebhookConfigResponse> webhookConfigResponse = results.stream().map( config -> new WebhookConfigResponse(publishedHost + "/" + String.join("/", "webhooks", currentVersion, config.getWebhookId()), config)).collect( Collectors.toList() );
                                    return ServerResponse.ok().bodyValue(webhookConfigResponse);
                                })
                                .doOnError(throwable -> ServerResponse.badRequest().bodyValue(throwable.getMessage())), docs -> {
                    docs.parameter(org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder().required(false).name("page").example("0").description("Page number, default 0"))
                            .parameter(org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder().required(false).name("size").example("10").description("Page size, can be any number between 1 to 20 max, default 10"))
                            .response(org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder().responseCode("200").implementationArray(WebhookConfig.class));
                    xFabricCorrelationDocs.andThen(notFound404Response).accept(docs);
                    docs.operationId("getAllWebhooksForAProject");
                }).build())
                .and(route().GET(idRestUrl, contentType(APPLICATION_JSON), serverRequest ->
                        repository.findByWebhookId(serverRequest.pathVariable(webhookIdPathVar))
                                .log(this.getClass().getName(), FINE)
                                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, serverRequest.pathVariable(webhookIdPathVar) + NOT_FOUND_SUFFIX)))
                                .flatMap(webhookConfig -> ServerResponse.ok().bodyValue(webhookConfig))
                                .doOnError(throwable -> ServerResponse.badRequest().bodyValue(throwable.getMessage())), docs -> {
                    docs.parameter(org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder().in(ParameterIn.PATH).name(webhookIdPathVar))
                            .response(org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder().responseCode("200").implementation(WebhookConfig.class));
                    xFabricCorrelationDocs.andThen(notFound404Response).accept(docs);
                    docs.operationId("getWebhookDetails");
                }).build())
                .and(route().DELETE(idRestUrl, contentType(APPLICATION_JSON), serverRequest ->
                        repository.findByWebhookId(serverRequest.pathVariable(webhookIdPathVar))
                                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, serverRequest.pathVariable(webhookIdPathVar) + NOT_FOUND_SUFFIX)))
                                .log(this.getClass().getName(), FINE)
                                .flatMap(repository::delete)
                                .then(ServerResponse.status(HttpStatus.OK).build())
                                .doOnError(throwable -> log.error("{}Failed deleting webhook {} due to error {}", serverRequest.exchange().getLogPrefix(), serverRequest.pathVariable(webhookIdPathVar), throwable.getMessage(), throwable)), docs -> {
                    docs.parameter(org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder().in(ParameterIn.PATH).name(webhookIdPathVar));
                    xFabricCorrelationDocs.andThen(notFound404Response).accept(docs);
                    docs.operationId("deleteWebhook");
                }).build())
                .filter((request, next) -> {
                    if (request.queryParam("size").isPresent() && Integer.parseInt(request.queryParam("size").get()) > 20)
                        return Mono.error(new ServerWebInputException("cannot process page size of more than 20"));
                    return next.handle(request);
                });
    }

    private String generateToken(String data) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(data.toCharArray(), generateRandomString().getBytes(), 100000, 128);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] iv = new byte[16];
        random.nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
        return Base64.getEncoder()
                .encodeToString(cipher.doFinal(data.getBytes(StandardCharsets.UTF_8))).replaceAll("[^\\w\\s]", "");
    }

    private String generateRandomString() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return DigestUtils.sha3_224Hex(bytes);
    }

    @SuppressWarnings("java:S1612")
    private String generateWebhookId() {
        return random.ints(48, 123)
                .filter(num -> (num < 58 || num > 64) && (num < 91 || num > 96))
                .limit(15)
                .mapToObj(c -> (char) c)
                .collect(StringBuffer::new, StringBuffer::append, StringBuffer::append)
                .toString();
    }

    private static final Consumer<Builder> created201Response = ops ->
            ops.response(responseBuilder().responseCode(String.valueOf(HttpStatus.CREATED))
                    .content(org.springdoc.core.fn.builders.content.Builder.contentBuilder().mediaType(APPLICATION_JSON_VALUE)
                            .example(org.springdoc.core.fn.builders.exampleobject.Builder.exampleOjectBuilder().name("webhookId").value("cuelohK0ji0za"))));

    private static final Consumer<Builder> notFound404Response = ops ->
            ops.response(responseBuilder().responseCode(String.valueOf(HttpStatus.NOT_FOUND))
                    .description("Specified webhook could not be found"));

    private static final Consumer<Builder> badRequestBodyResponse = ops -> ops
            .response(responseBuilder().responseCode(String.valueOf(HttpStatus.BAD_REQUEST))
                    .description("Request body is missing required fields or not in right format"));

    private static final Consumer<Builder> cryptoAlgoFailedResponse = ops -> ops
            .response(responseBuilder().responseCode(String.valueOf(HttpStatus.NOT_IMPLEMENTED))
                    .description("Failed generating token due to missing encryption algorithms, this is usually a deployment configuration problem"));

    private static final Consumer<Builder> xFabricCorrelationDocs = ops -> {
        ops.parameter(parameterBuilder().in(ParameterIn.HEADER).name("X-FABRIC-CORRELATION")
                .description("contains 'realmId:accountId:userId' colon separated")
                .example("THEREALM:THEACCOUNTID:THEUSER"));
        ops.response(responseBuilder().responseCode(String.valueOf(HttpStatus.UNAUTHORIZED)).description("Requests missing the X-FABRIC-CORRELATION headers is considered and unauthorized request"));
    };

}
