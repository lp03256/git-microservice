package com.digite.cloud.vcs.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
@AllArgsConstructor
@Builder
@JsonDeserialize(builder = GitRequest.GitRequestBuilder.class)
public class GitRequest {

    @JsonProperty
    private final String commitTime;

    @JsonProperty
    private final String commitMessage;

    @JsonProperty
    private final String branch;

    @JsonProperty
    private final String before;

    @JsonProperty
    private final String compare;

    @JsonProperty
    private  final String after;

    @JsonProperty
    private final String name;

    @JsonProperty
    private final String url;

    @JsonProperty
    private final String description;

    @JsonProperty
    private final String commitAvatar;

    @JsonProperty
    private final String commitName;

    @JsonProperty
    private final List<String> added;

    @JsonProperty
    private final List<String> modified;

    @JsonProperty
    private final List<String> removed;

}
