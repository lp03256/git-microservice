package com.digite.cloud.vcs.mappings;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

@Component
public class MappingDataService {
    public Map<String, String> getMappingForProject( String vcsname) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(
                new String( Files.readAllBytes( Paths.get(new ClassPathResource( vcsname + "-mapper.json" ).getFile().getAbsolutePath()))), Map.class);
    }
}
