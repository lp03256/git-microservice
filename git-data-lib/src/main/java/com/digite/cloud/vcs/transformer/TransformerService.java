package com.digite.cloud.vcs.transformer;

import lombok.Getter;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class TransformerService {

    private final StandardEvaluationContext standardEvaluationContext;

    @Getter
    private final Map<String, Map<String, Expression>> expressionsMap;

    public TransformerService ( StandardEvaluationContext standardEvaluationContext,
                                Map<String, Map<String, Expression>> expressionsMap ) {
        this.standardEvaluationContext = standardEvaluationContext;
        this.expressionsMap = expressionsMap;
    }

    public Map<String, String> generateFinalJson ( Map<String, Object> payload, Map<String, String> transformationMap ) {
        Map<String, Expression> mappingExpressions = getParsedMappingExpressions( transformationMap);
        Map<String, String> skCardData = mappingExpressions.entrySet().stream().collect(
                Collectors.toMap( entry -> entry.getKey(),
                        entry -> entry.getValue().getValue(this.standardEvaluationContext, payload,
                                String.class) == null ? "" : entry.getValue().getValue(this.standardEvaluationContext, payload,
                                String.class)));
        return skCardData;
    }

    private Map<String, Expression> getParsedMappingExpressions ( Map<String, String> transformationMap ) {
        Map<String, String> issueDataExpressions = Optional.of(
                transformationMap).orElseThrow(()-> new RuntimeException());
        Map<String, Expression> parsedExpressionsMap = issueDataExpressions.entrySet()
                .stream()
                .collect(
                        Collectors.toMap(entry -> entry.getKey(),
                                entry -> new SpelExpressionParser().parseExpression(entry.getValue())));
        return parsedExpressionsMap;
    }
}
