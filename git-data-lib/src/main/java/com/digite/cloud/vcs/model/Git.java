package com.digite.cloud.vcs.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@FieldNameConstants
@ToString
@Builder
@Document(collection = "git_data")
public class Git implements Serializable {

    @Indexed(unique = true)
    private String gitId;
    private String commitTime;
    private String commitMessage;
    private String realmId;
    private String app;
    private String accountId;
    private String ownerId;
    @Setter(onMethod_ = {@JsonProperty })
    private Changes changes;
    @Setter(onMethod_ = {@JsonProperty })
    private Repo repo;
    @Setter(onMethod_ = {@JsonProperty })
    private Commitby commitBy;
    private String branch;
    private String before;
    private String after;
    private String compare;
    private String itemCode;
    private String event;

    @Id
    @JsonIgnore
    private String id;

    @CreatedDate
    @JsonIgnore
    private Date recordCreatedAt;

    @LastModifiedDate
    @JsonIgnore
    private Date recordLastModifiedDate;

    @Version
    @JsonIgnore
    private Long version;
}
