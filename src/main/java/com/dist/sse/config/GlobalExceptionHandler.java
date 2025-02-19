package com.dist.sse.config;

// Imports for logging and Spring framework components
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

// Exception imports
import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.TimeoutException;

// @ControllerAdvice allows handling exceptions globally across the whole application
@ControllerAdvice
public class GlobalExceptionHandler {
    // Logger instance for this class
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Handles IOException (Input/Output exceptions)
    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException ex) {
        logger.error("IO Exception occurred: ", ex);
        // Special handling for broken pipe errors (client disconnection)
        if (ex.getMessage().contains("Broken pipe")) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body("Connection closed by client");
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An IO error occurred");
    }

    // Handles SocketException (network-related errors)
    @ExceptionHandler(SocketException.class)
    public ResponseEntity<String> handleSocketException(SocketException ex) {
        logger.error("Socket Exception occurred: ", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Connection reset");
    }

    // Handles async request timeouts
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<String> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException ex) {
        logger.warn("Async request timed out: ", ex);
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body("Server-Sent Events connection timed out");
    }

    // Handles general timeout exceptions
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<String> handleTimeoutException(TimeoutException ex) {
        logger.error("Timeout Exception occurred: ", ex);
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body("Operation timed out");
    }

    // Handles illegal state exceptions
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalStateException(IllegalStateException ex) {
        logger.error("Illegal State Exception occurred: ", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Illegal state: " + ex.getMessage());
    }

    // Catch-all handler for any unhandled exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        logger.error("Unexpected error occurred: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred");
    }
}