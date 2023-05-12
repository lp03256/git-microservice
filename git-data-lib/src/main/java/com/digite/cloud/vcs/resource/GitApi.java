package com.digite.cloud.vcs.resource;

import com.digite.cloud.vcs.model.Git;
import com.digite.cloud.vcs.repository.GitRepository;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.logging.Level;

public class GitApi {

    private final GitRepository repository;

    public GitApi ( GitRepository repository ) {this.repository = repository;}

    public Mono<ServerResponse> getGitData ( String realmId, String accountId, String ownerId, String itemCode ) {
        return repository.findByRealmIdAndAccountIdAndOwnerIdAndItemCode(
                        realmId,
                        accountId,
                        ownerId,
                        itemCode)
                .log(this.getClass().getName(), Level.FINE)
                .collectList()
                .switchIfEmpty(Mono.error(new ResponseStatusException( HttpStatus.NO_CONTENT, "No Content available")))
                .flatMap(results ->  ServerResponse.ok().bodyValue(results) )
                .doOnError(throwable -> ServerResponse.badRequest().bodyValue(throwable.getMessage()));
    }

    public Mono<JSONArray> getGitResponseData(String realmId, String accountId, String ownerId, String itemCode) {
        return repository.findByRealmIdAndAccountIdAndOwnerIdAndItemCode(realmId, accountId, ownerId, itemCode)
                .log(this.getClass().getName(), Level.FINE)
                .collectList()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NO_CONTENT, "No Content available")))
                .flatMap(results -> {
                    JSONArray jsonArray = new JSONArray();
                    for (Object result : results) {
                        jsonArray.put(convertToJSONObject( (Git) result ));
                    }
                    return Mono.just(jsonArray);
                })
                .doOnError(throwable -> Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, throwable.getMessage())));
    }

    public JSONObject convertToJSONObject(Git git) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("gitId", git.getGitId());
        json.put("commitTime", git.getCommitTime());
        json.put("commitMessage", git.getCommitMessage());
        json.put("realmId", git.getRealmId());
        json.put("app", git.getApp());
        json.put("accountId", git.getAccountId());
        json.put("ownerId", git.getOwnerId());

        JSONObject changesJson = new JSONObject();

        String[] addedChanges = git.getChanges().getAdded();
        JSONArray addedJsonArray = new JSONArray();
        for (String change : addedChanges) {
            addedJsonArray.put(change);
        }

        String[] removedChanges = git.getChanges().getRemoved();
        JSONArray removedJsonArray = new JSONArray();
        for (String change : removedChanges) {
            removedJsonArray.put(change);
        }

        String[] modifiedChanges = git.getChanges().getModified();
        JSONArray modifiedJsonArray = new JSONArray();
        for (String change : modifiedChanges) {
            modifiedJsonArray.put(change);
        }

        changesJson.put("added", addedJsonArray);
        changesJson.put("modified", modifiedJsonArray);
        changesJson.put("removed", removedJsonArray);
        json.put("changes", changesJson);

        JSONObject repoJson = new JSONObject();
        repoJson.put("name", git.getRepo().getName());
        repoJson.put("url", git.getRepo().getUrl());
        repoJson.put("description", git.getRepo().getDescription());
        json.put("repo", repoJson);

        JSONObject commitByJson = new JSONObject();
        commitByJson.put("name", git.getCommitBy().getName());
        commitByJson.put("avatar", git.getCommitBy().getAvatar());
        json.put("commitBy", commitByJson);

        json.put("branch", git.getBranch());
        json.put("before", git.getBefore());
        json.put("after", git.getAfter());
        json.put("compare", git.getCompare());
        json.put("itemCode", git.getItemCode());
        json.put("event", git.getEvent());

        return json;
    }





}
