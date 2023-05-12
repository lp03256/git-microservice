package com.digite.cloud.vcs.push;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@Builder
@ToString
@Getter(AccessLevel.PACKAGE)
@Setter(AccessLevel.NONE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GitExecution {

    private final Map<String, Object> payload;
}
