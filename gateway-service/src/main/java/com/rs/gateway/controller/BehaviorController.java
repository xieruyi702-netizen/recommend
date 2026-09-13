package com.rs.gateway.controller;

import com.rs.behavior.api.BehaviorEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/behavior")
public class BehaviorController {

    private final KafkaTemplate<String, BehaviorEvent> kafkaTemplate;

    public BehaviorController(KafkaTemplate<String, BehaviorEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** 埋点上报：异步投递到 Kafka，削峰并解耦主链路 */
    @PostMapping("/report")
    public Map<String, Object> report(@RequestBody BehaviorEvent event) {
        if (event.getTimestamp() == 0) {
            event.setTimestamp(System.currentTimeMillis());
        }
        kafkaTemplate.send("user-behavior", String.valueOf(event.getUserId()), event);
        return Map.of("ok", true, "eventId", UUID.randomUUID().toString());
    }
}
