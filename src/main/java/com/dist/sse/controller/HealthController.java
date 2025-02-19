package com.dist.sse.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController combines @Controller and @ResponseBody
// Indicates that this class handles HTTP requests and returns data directly (not views)
@RestController
public class HealthController {

    // @GetMapping maps HTTP GET requests to "/health" to this method
    // This creates an endpoint at http://<server>/health
    @GetMapping("/health")
    public String health() {
        // Simply returns "OK" when the endpoint is called
        // This response indicates that the application is running
        return "OK";
    }
}