package com.digite.cloud.vcs.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import org.springframework.data.annotation.PersistenceCreator;

import java.io.Serializable;

import static lombok.AccessLevel.PRIVATE;

@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor(onConstructor_ = {@PersistenceCreator})
@NoArgsConstructor(force = true, access = PRIVATE)
@Builder
@JsonDeserialize(builder = Repo.RepoBuilder.class)
public class Repo implements Serializable {

    @JsonProperty
    String name;

    @JsonProperty
    String url;

    @JsonProperty
    String description;

}
