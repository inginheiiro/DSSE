// This line declares the package name which helps organize classes and avoid naming conflicts
package com.dist.sse;

// These are necessary imports:
// - Logger and LoggerFactory for logging functionality
// - SpringApplication and SpringBootApplication for Spring Boot framework
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication is a convenience annotation that adds all of the following:
// - @Configuration: Tags the class as a source of bean definitions
// - @EnableAutoConfiguration: Tells Spring Boot to start adding beans based on classpath settings
// - @ComponentScan: Tells Spring to look for other components, configurations, and services
@SpringBootApplication
public class ChallengeApplication {
    // Creates a logger instance for this class using SLF4J (Simple Logging Facade for Java)
    // This is a good practice to log important application events
    private static final Logger logger = LoggerFactory.getLogger(ChallengeApplication.class);

    // The main entry point of the Spring Boot application
    public static void main(String[] args) {
        // Launches the Spring application context, starting the whole application
        SpringApplication.run(ChallengeApplication.class, args);

        // Logs an information message indicating that the application has started successfully
        // and is ready to accept connections
        logger.info("Application fully started and ready to accept connections");
    }
}