package com.digite.cloud.vcs.repository;

import com.digite.cloud.vcs.model.Git;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface GitRepository extends ReactiveMongoRepository<Git, Integer> {
    Flux<Git> findByRealmIdAndAccountIdAndOwnerId( String realmId, String accountId, String ownerId, Pageable pageable);

    Flux<Git> findByRealmIdAndAccountIdAndOwnerIdAndItemCode( String realmId, String accountId, String ownerId, String itemCode);

}
