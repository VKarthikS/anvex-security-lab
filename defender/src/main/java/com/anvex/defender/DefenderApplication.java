package com.anvex.defender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.anvex.defender.config.DefenderProperties;

@SpringBootApplication
@EnableConfigurationProperties(DefenderProperties.class)
public class DefenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(DefenderApplication.class, args);
    }
}
