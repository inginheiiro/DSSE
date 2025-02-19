package com.dist.sse.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Data                   // Generates getters, setters, toString, equals, hashCode
@NoArgsConstructor      // Generates default constructor
@AllArgsConstructor     // Generates constructor with all fields
@Builder                // Generates builder pattern methods
public class QueueMessage implements Serializable {
    private String id;
    private String content;
    private boolean processed;
    @Builder.Default    // Sets default value when using builder
    private Instant timestamp = Instant.now();
    private long sequence;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @JsonProperty("readableTimestamp")
    @JsonSerialize(using = ToStringSerializer.class)
    public String getReadableTimestamp() {
        return formatter.format(this.timestamp);
    }

    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting QueueMessage to JSON", e);
        }
    }
}