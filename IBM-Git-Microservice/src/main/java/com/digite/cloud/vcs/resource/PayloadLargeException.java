package com.digite.cloud.vcs.resource;

import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.PAYLOAD_TOO_LARGE, reason = "payload should not exceed 10kb")
public class PayloadLargeException extends RuntimeException {
    public PayloadLargeException ( @NonNull String message ) {
        super(message);
    }
}
