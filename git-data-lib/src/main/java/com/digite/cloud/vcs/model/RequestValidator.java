package com.digite.cloud.vcs.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.server.ServerWebInputException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class RequestValidator {

    private final Validator validator;

    public <T> void validate(T obj) {
        log.debug("Calling validator for payload {}", obj);
        if (obj == null) {
            throw new IllegalArgumentException();
        }
        Errors errors = new BeanPropertyBindingResult(obj, obj.getClass().getSimpleName());
        this.validator.validate(obj, errors);
        if (errors.hasErrors()) {
            List<String> validationErrors = errors.getFieldErrors().stream().map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage()).collect( Collectors.toList());
            throw new ServerWebInputException(String.join(", ", validationErrors));
        }
    }
}
