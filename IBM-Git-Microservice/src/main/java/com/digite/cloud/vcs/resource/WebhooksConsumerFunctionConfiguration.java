package com.digite.cloud.vcs.resource;

import com.digite.cloud.vcs.mappings.MappingDataService;
import com.digite.cloud.vcs.model.*;
import com.digite.cloud.vcs.repository.GitRepository;
import com.digite.cloud.vcs.repository.WebhookConfigRepository;
import com.digite.cloud.vcs.transformer.TransformerService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.*;
import java.util.logging.Level;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Slf4j
@Configuration
@ConditionalOnExpression("${digite.cloud.rest.enabled:false}")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class WebhooksConsumerFunctionConfiguration {

    private final TransformerService transformerService;
    private final MappingDataService mappingDataService;

    public static final String OWNER_TYPE_KEY = "category";
    public static final String OWNER_ID_KEY = "ownerId";
    public static final String SOURCE_APP_KEY = "app";
    public static final String REALM_ID_KEY = "realmId";

    public static final String CONTENT_COMMITS = "content.commits[";

    public static final String COMMITMESSAGE = "commitMessage";

    public static final String CHANGES_MODIFIED = "changes.modified";

    public static final String CHANGES_REMOVED = "changes.removed";

    public static final String CHANGES_ADDED = "changes.added";
    public static final String ACCOUNT_ID_KEY = "accountId";
    public static final String EVENT_NAME_KEY = "eventName";
    public static final String ENTITY_VERSION_KEY = "entityVersion";
    public static final String ENTITY_KEY = "entity";

    public static final String COMMITBY_NAME = "commitBy.name";

    public static final String COMMIT_TIME = "commitTime";

    public static final String COMPARE = "compare";

    private final StreamBridge streamBridge;
    private final SecureRandom random = new SecureRandom();
    private final WebhookConfigRepository repository;

    private final GitRepository gitRepository;

    @Value("${digite.cloud.allowed.payload.size:10KB}")
    public String allowedPayloadSize;

    @Setter(onMethod_ = {@Value("${digite.cloud.fabric.headers.fabric-auth}")})
    private String fabricAuthHeader;
    @Setter(onMethod_ = {@Value("${digite.cloud.fabric.runtimeEnv:local}")})
    private String namespace;
    @Setter(onMethod_ = {@Value("${digite.cloud.swiftalk.piglet.kafka.webhook-request-id-header:X-REQUEST-ID}")})
    private String webhookRequestIdHeader;
    @Setter(onMethod_ = {@Value("${digite.cloud.swiftalk.piglet.webhook-id-path-var:webhookId}")})
    private String webhookIdPathVar;

    @Bean
    public RouterFunction<ServerResponse> webhooksConsumer() {
        //noinspection UnnecessaryUnboxing
        return route().POST("/webhooks/v1/{webhookId}", accept(MediaType.APPLICATION_JSON), serverRequest ->
                repository.findByWebhookId(serverRequest.pathVariable(webhookIdPathVar))
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "webhook does not exist")))
                        .doOnNext(webhookConfig -> {
                            //noinspection ConstantConditions
                            if (!serverRequest.headers().header(webhookConfig.getTokenHeaderName()).isEmpty() && !serverRequest.headers().firstHeader(webhookConfig.getTokenHeaderName()).equals(webhookConfig.getTokenHeaderValue()))
                                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "webhook token header is invalid");
                        })
                        .flatMap(webhookConfig -> serverRequest.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "webhook does not exist")))
                                .flatMap(payload -> {
                                    log.debug("{}Processing HTTP Payload with key {}", serverRequest.exchange().getLogPrefix(), serverRequest.pathVariable(webhookIdPathVar));

                                    Map<String, String> webhookProps = new HashMap<String, String>() {{
                                        put(ACCOUNT_ID_KEY, webhookConfig.getAccountId());
                                        put(SOURCE_APP_KEY, webhookConfig.getApp());
                                        put(ENTITY_KEY, "com.digite.cloud.External");
                                        put(ENTITY_VERSION_KEY, "0");
                                        put(EVENT_NAME_KEY, "webhookPush");
                                        put(REALM_ID_KEY, webhookConfig.getRealmId());
                                        put(OWNER_ID_KEY, webhookConfig.getProjectId());
                                        put(OWNER_TYPE_KEY, webhookConfig.getCategory());
                                    }};
                                    return gitDataProcessor( webhookProps, payload);
                                }))
                        .flatMap(sent -> ServerResponse.ok().build())
                        .log(this.getClass().getName(), Level.FINE)
                        .doOnError(e -> log.error("{}Processing webhook Id {} failed due to error {}", serverRequest.exchange().getLogPrefix(), serverRequest.pathVariable(webhookIdPathVar), e.getMessage(), e))
        )
        .build()
        .filter((request, next) -> {
            int value = (int)request.headers().contentLength().getAsLong();
            Long allowedPayloadLimit = DataSize.parse(allowedPayloadSize).toBytes();
            if (request.headers().contentLength().isPresent() && value > allowedPayloadLimit.intValue())
                return Mono.error(new PayloadLargeException("cannot process page size of more than " + allowedPayloadSize));
            return next.handle(request);
        });
    }

    public Mono<List<Git>> gitDataProcessor ( Map<String, String> event, Map<String, Object> content ) {
        log.debug( "Extracted Fabric Event {}", event );
        String app = event.get( "app" );

        String[] params = new String[4];
        params[0] = event.get( REALM_ID_KEY );
        params[1] = event.get( ACCOUNT_ID_KEY );
        params[2] = event.get( OWNER_ID_KEY );
        params[3] = app;

        ArrayList<Object> commits = (ArrayList<Object>) content.get( "commits" );
        ArrayList<Mono<Git>> saveMono = new ArrayList<>();

        for (int i = 0; i < commits.size(); i++) {
            Map<String, String> transformationMap = null;
            transformationMap = getMappingForProject( app );

            if ("gitlab".equals( app )) {
                transformationMap.put( COMMITMESSAGE, CONTENT_COMMITS+i+"].title" );
                transformationMap.put( COMMITBY_NAME, CONTENT_COMMITS+i+"].author.name" );
                transformationMap.put( COMPARE, CONTENT_COMMITS+i+"].url" );
            } else {
                transformationMap.put( COMMITMESSAGE, CONTENT_COMMITS+i+"].message" );
            }

            transformationMap.put( COMMIT_TIME, CONTENT_COMMITS+i+"].timestamp" );
            transformationMap.put( CHANGES_ADDED, CONTENT_COMMITS+i+"].added" );
            transformationMap.put( CHANGES_MODIFIED, CONTENT_COMMITS+i+"].modified" );
            transformationMap.put( CHANGES_REMOVED, CONTENT_COMMITS+i+"].removed" );

            log.debug( "Transformation Map {}", transformationMap );


            Map<String, String> result = transformerService.generateFinalJson( new HashMap<String, Object>() {{
                put("event", event);
                put("content", content);
            }}, transformationMap);
            log.debug( "Result {}", result );

            saveMono.add( saveDatainMongo(result, params) );
        }
        return Flux.concat(saveMono).collectList();
    }

    private Map<String, String>  getMappingForProject(String app) {
        Map<String, String> map = new HashMap<>();

        if ("github".equals( app )) {
            map.put( "branch", "content.repository.default_branch" );
            map.put( "commitMessage", "content.commits.message" );
            map.put( "commitTime", "content.commits.timestamp" );
            map.put( "before", "content.before" );
            map.put( "after", "content.after" );
            map.put( "changes.added", "content.commits.added" );
            map.put( "changes.modified", "content.commits.modified" );
            map.put( "changes.removed", "content.commits.removed" );
            map.put( "repo.name", "content.repository.name" );
            map.put( "repo.url", "content.repository.url" );
            map.put( "repo.description", "content.repository.description" );
            map.put( "commitBy.name", "content.pusher.name" );
            map.put( "commitBy.avatar", "content.sender.avatar_url" );
            map.put( "compare", "content.compare" );
        } else {
            map.put( "branch", "content.ref" );
            map.put( "commitMessage", "content.commits.title" );
            map.put( "commitTime", "content.commits.timestamp" );
            map.put( "before", "content.before" );
            map.put( "after", "content.after" );
            map.put( "changes.added", "content.commits.added" );
            map.put( "changes.modified", "content.commits.modified" );
            map.put( "changes.removed", "content.commits.removed" );
            map.put( "repo.name", "content.repository.name" );
            map.put( "repo.url", "content.repository.git_http_url" );
            map.put( "repo.description", "content.repository.description" );
            map.put( "commitBy.name", "content.commits.author.name" );
            map.put( "commitBy.avatar", "content.user_avatar" );
        }

        return map;
    }
    private Mono<Git> saveDatainMongo ( Map<String, String> result, String[] params ) {
        String itemCode = String.valueOf( result.get(COMMITMESSAGE) ).split( " " )[0];

        GitRequest request = GitRequest.builder()
                .commitMessage( String.valueOf( result.get(COMMITMESSAGE) ) )
                .commitTime( String.valueOf( result.get(COMMIT_TIME) ) )
                .before( String.valueOf( result.get( "before" ) ) )
                .after( String.valueOf( result.get( "after" ) ) )
                .before( String.valueOf( result.get( "before" ) ) )
                .compare( String.valueOf( result.get( COMPARE ) ) )
                .commitName( String.valueOf( result.get( COMMITBY_NAME ) ) )
                .commitAvatar( String.valueOf( result.get( "commitBy.avatar" ) ) )
                .name( String.valueOf( result.get( "repo.name" ) ) )
                .branch( String.valueOf( result.get( "branch" ) )  )
                .url( String.valueOf( result.get( "repo.url" ) ) )
                .description( String.valueOf( result.get( "repo.description" ) ) )
                .added(result.get( CHANGES_ADDED ).length() > 0 ?
                        new ArrayList<>( Arrays.asList(result.get( CHANGES_ADDED ).split(","))) :  new ArrayList<>()  )
                .modified(result.get( CHANGES_MODIFIED ).length() > 0 ?
                        new ArrayList<>( Arrays.asList(result.get( CHANGES_MODIFIED ).split(","))) :  new ArrayList<>()  )
                .removed(result.get( CHANGES_REMOVED ).length() > 0 ?
                        new ArrayList<>( Arrays.asList(result.get( CHANGES_REMOVED ).split(","))) : new ArrayList<>())
                .build();

        return gitRepository.save( Git.builder()
                .itemCode( itemCode )
                .accountId(params[1])
                .ownerId(params[2])
                .realmId(params[0])
                .event( "push" )
                .gitId(String.join("-", "W", String.valueOf(random.nextLong())))
                .commitMessage( request.getCommitMessage())
                .commitTime( request.getCommitTime() )
                .changes( new Changes(request.getAdded().toArray( new String[0] ), request.getModified().toArray( new String[0] ),
                        request.getRemoved().toArray( new String[0] ) ) )
                .repo( new Repo( request.getName(), request.getUrl(), request.getDescription()) )
                .commitBy( new Commitby( request.getCommitName(), request.getCommitAvatar() ) )
                .branch( request.getBranch() )
                .after( request.getAfter() )
                .before( request.getBefore() )
                .compare( request.getCompare() )
                .build()  );
    }

}

