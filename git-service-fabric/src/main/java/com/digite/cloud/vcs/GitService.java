package com.digite.cloud.vcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;

@SpringBootApplication
@EnableReactiveMongoAuditing
@ConfigurationPropertiesScan(basePackages = {"com.digite.cloud"})
public class GitService {

	public static void main(String[] args) {
		SpringApplication.run( GitService.class, args);
	}

}
