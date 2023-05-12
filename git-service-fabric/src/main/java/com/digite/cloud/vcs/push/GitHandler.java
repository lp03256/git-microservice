package com.digite.cloud.vcs.push;

import com.digite.cloud.vcs.mappings.MappingDataService;
import com.digite.cloud.vcs.model.*;
import com.digite.cloud.vcs.repository.GitRepository;
import com.digite.cloud.vcs.transformer.TransformerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;

@Slf4j
@Configuration
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class GitHandler {

    private final SecureRandom random = new SecureRandom();
    private final GitRepository repository;
    private final TransformerService transformerService;
    private final MappingDataService mappingDataService;
    public static final String COMMITMESSAGE = "commitMessage";
    public static final String CONTENT_COMMITS = "content.commits[";
    public static final String CHANGES_ADDED = "changes.added";
    public static final String CHANGES_MODIFIED = "changes.modified";
    public static final String CHANGES_REMOVED = "changes.removed";

    @SuppressWarnings("unchecked")
    @Bean
    public Function<Flux<Message<Map<String, Object>>>, Mono<Void>> handle() {
        return flux -> flux.flatMap(message -> {
                    try {
                        Map<String, String> event = (Map<String, String>) message.getPayload().get( "event" );
                        Map<String, Object> content = (Map<String, Object>) message.getPayload().get( "content" );

                        log.debug( "Extracted Fabric Event {}", event );
                        String app = event.get( "app" );

                        String[] params = new String[4];
                        params[0] = event.get( "realmId" );
                        params[1] = event.get( "accountId" );
                        params[2] = event.get( "ownerId" );
                        params[3] = app;

                        ArrayList<Object> commits = (ArrayList<Object>) content.get( "commits" );
                        Map<String, String> transformationMap = null;
                        for (int i = 0; i < commits.size(); i++) {
                            transformationMap = mappingDataService.getMappingForProject( app );
                            if ("gitlab".equals( app )) {
                                transformationMap.put( COMMITMESSAGE, CONTENT_COMMITS+i+"].title" );
                                transformationMap.put( "commitBy.name", CONTENT_COMMITS+i+"].author.name" );
                                transformationMap.put( "compare", CONTENT_COMMITS+i+"].url" );
                            } else {
                                transformationMap.put( COMMITMESSAGE, CONTENT_COMMITS+i+"].message" );
                            }

                            transformationMap.put( "commitTime", CONTENT_COMMITS+i+"].timestamp" );
                            transformationMap.put( CHANGES_ADDED, CONTENT_COMMITS+i+"].added" );
                            transformationMap.put( CHANGES_MODIFIED, CONTENT_COMMITS+i+"].modified" );
                            transformationMap.put( CHANGES_REMOVED, CONTENT_COMMITS+i+"].removed" );

                            log.debug( "Transformation Map {}", transformationMap );

                            Map<String, String> result = transformerService.generateFinalJson(message.getPayload(), transformationMap);
                            log.debug( "Result {}", result );

                            saveDatainMongo(result, params);

                        }

                    } catch ( Exception e ) {
                        log.error( e.getMessage() );
                        return Mono.error(e);
                    }


                    return Mono.just( GitExecution.builder().payload(message.getPayload()).build() );
                })
                .log(this.getClass().getName(), Level.FINE)
                .onErrorContinue( (error, item)-> log.error("Error in GitHandler chain: " + error.getMessage() + " " + item) )
                .doOnError(throwable -> log.error("failed processing message due to error {}", throwable.getMessage(), throwable))
                .then();
    }

    private void saveDatainMongo ( Map<String, String> result, String[] params ) {
        String itemCode = String.valueOf( result.get(COMMITMESSAGE) ).split( " " )[0];

        GitRequest request = GitRequest.builder()
                .commitMessage( String.valueOf( result.get(COMMITMESSAGE) ) )
                .commitTime( String.valueOf( result.get("commitTime") ) )
                .before( String.valueOf( result.get( "before" ) ) )
                .after( String.valueOf( result.get( "after" ) ) )
                .before( String.valueOf( result.get( "before" ) ) )
                .compare( String.valueOf( result.get( "compare" ) ) )
                .commitName( String.valueOf( result.get( "commitBy.name" ) ) )
                .commitAvatar( String.valueOf( result.get( "commitBy.avatar" ) ) )
                .name( String.valueOf( result.get( "repo.name" ) ) )
                .branch( String.valueOf( result.get( "branch" ) )  )
                .url( String.valueOf( result.get( "repo.url" ) ) )
                .description( String.valueOf( result.get( "repo.description" ) ) )
                .added(result.get( CHANGES_ADDED ).length() > 0 ?
                        new ArrayList<>( Arrays.asList(result.get( CHANGES_ADDED ).split(","))) :  new ArrayList<>()  )
                .modified(result.get( CHANGES_MODIFIED ).length() > 0 ?
                        new ArrayList<>( Arrays.asList(result.get(CHANGES_MODIFIED ).split(","))) :  new ArrayList<>()  )
                .removed(result.get( CHANGES_REMOVED ).length() > 0 ?
                        new ArrayList<>( Arrays.asList(result.get( CHANGES_REMOVED ).split(","))) : new ArrayList<>())
                .build();

        repository.save( Git.builder()
                .itemCode( itemCode )
                .accountId(params[1])
                .ownerId(params[2])
                .realmId(params[0])
                .app( params[3] )
                .event( "push" )
                .gitId(String.join("-", "W", String.valueOf(random.nextLong(10000000L, 99999999L))))
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
                .build()  ).block();
    }
}