package com.example.pispringai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PiSpringAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PiSpringAiApplication.class, args);
    }
}
