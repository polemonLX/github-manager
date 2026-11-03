package com.polemon.github_manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point for the GitHub Manager Spring Boot application.
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
public class Main {

    /**
     * Bootstraps and runs the Spring application context.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    static void main(String[] args) {
        SpringApplication.run(Main.class, args);

        log.info("GitHub Manager application started successfully.");
    }

}
