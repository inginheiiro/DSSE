
package com.dist.sse.config;

// Necessary Spring Framework imports for configuration
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

// @Configuration marks this class as a source of bean definitions for the Spring container
// @EnableScheduling enables Spring's scheduled task execution capability
@Configuration
@EnableScheduling
public class AppConfig {

    // @Bean annotation tells Spring that this method will return an object
    // that should be registered as a bean in the Spring application context
    @Bean
    public RestTemplate restTemplate() {
        // Creates and returns a new RestTemplate instance
        // RestTemplate is Spring's central class for client-side HTTP communication
        return new RestTemplate();
    }
}