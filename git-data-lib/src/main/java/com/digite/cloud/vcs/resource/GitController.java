package com.digite.cloud.vcs.resource;

import com.digite.cloud.vcs.model.Git;
import com.digite.cloud.vcs.model.GitRequest;
import com.digite.cloud.vcs.model.RequestValidator;
import com.digite.cloud.vcs.repository.GitRepository;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import javax.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Objects;
import java.util.logging.Level;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class GitController {

    private final GitRepository repository;

    private final RequestValidator requestValidator;
    private final SecureRandom random = new SecureRandom();

    private String gitId;

    private String fabricAuthHeader;
    private String ownerIdHeader;

    @Value("${digite.cloud.fabric.headers.fabric-auth}")
    public void setFabricAuthHeader(String fabricAuthHeader) {
        this.fabricAuthHeader = fabricAuthHeader;
    }

    @Value("${digite.cloud.fabric.headers.owner-id}")
    public void setOwnerIdHeader(String ownerIdHeader) {
        this.ownerIdHeader = ownerIdHeader;
    }

    @Value("${digite.cloud.swiftalk.git.git-id:gitId}")
    public void setGitId(String gitId) {
        log.debug("Setting gitId to {}", gitId);
        this.gitId = gitId;
    }

    @Setter(onMethod_ = @Value("${digite.cloud.swiftalk.git.itemcode:itemcode}"))
    private String itemcode;

    @Bean
    public RouterFunction<ServerResponse> gitControllerEndpoints() {
        return RouterFunctions.route()
                .GET( "/rest/v1/git", accept(APPLICATION_JSON), serverRequest ->
                        repository.findByRealmIdAndAccountIdAndOwnerId(
                                        serverRequest.headers().firstHeader(fabricAuthHeader).split(":")[0],
                                        serverRequest.headers().firstHeader(fabricAuthHeader).split(":")[1],
                                        serverRequest.headers().firstHeader(ownerIdHeader).split(":")[0],
                                        PageRequest.of(Integer.parseInt(serverRequest.queryParam("page").orElse("0")), Integer.parseInt(serverRequest.queryParam("size").orElse("10"))))
                                .log(this.getClass().getName(), Level.FINE)
                                .collectList()
                                .switchIfEmpty(Mono.error(new ResponseStatusException( HttpStatus.NO_CONTENT, "No Content available")))
                                .flatMap(results -> ServerResponse.ok().bodyValue(results))
                                .doOnError(throwable -> ServerResponse.badRequest().bodyValue(throwable.getMessage()))
                )
                .GET( "/rest/v1/git/{"+itemcode+"}", accept(APPLICATION_JSON), serverRequest -> {
                            String realmId = serverRequest.headers().firstHeader(fabricAuthHeader).split(":")[0];
                            String accountId = serverRequest.headers().firstHeader(fabricAuthHeader).split(":")[1];
                            String ownerId = serverRequest.headers().firstHeader(ownerIdHeader).split(":")[0];
                            String itemCode = serverRequest.pathVariable(itemcode);
                            GitApi gitApi = new GitApi( repository );
                            return gitApi.getGitData(realmId, accountId, ownerId, itemCode);
                }
                )
                .POST("/rest/v1/git", accept(APPLICATION_JSON), serverRequest ->
                        serverRequest.bodyToMono( GitRequest.class)
                                .log(this.getClass().getName(), Level.FINE)
                                .switchIfEmpty(Mono.error(new ServerWebInputException("data cannot be empty")))
                                .doOnNext(requestValidator::validate)
                                .doOnError(throwable -> log.error("{}Git data creation failure due to validation error: {}", serverRequest.exchange().getLogPrefix(), throwable.getMessage(), throwable))
                                .flatMap(next -> repository.save(buildPushConfig(next,
                                                Objects.requireNonNull(serverRequest.headers().firstHeader(fabricAuthHeader)),
                                                serverRequest.headers().firstHeader(ownerIdHeader)))
                                        .doOnError(throwable -> log.error("{}Git data failure creation failed due to persistence error: {}", serverRequest.exchange().getLogPrefix(), throwable.getMessage(), throwable))
                                        .retry(3)
                                )
                                .flatMap(gitConfig -> ServerResponse.status(HttpStatus.CREATED).bodyValue(
                                        new HashMap<String, String>() {{
                                            put( gitId, gitConfig.getGitId() );
                                        }} )))
                .build() ;
    }

    private Git buildPushConfig (@NotNull GitRequest gitRequest, @NotNull String fabricAuthHeader, @NotNull String ownerId) {
        return   Git.builder()
                    .realmId(fabricAuthHeader.split(":")[0])
                    .accountId(fabricAuthHeader.split(":")[1])
                    .ownerId(ownerId)
                    .gitId(String.join("-", "W", String.valueOf(random.nextLong())) )
                    .commitMessage( gitRequest.getCommitMessage())
                    .commitTime( gitRequest.getCommitTime() ).build();
    }

}
