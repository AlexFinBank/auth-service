package uz.finbank.finbankauthservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Auth Service must keep working (register/login/refresh) even if Kafka is completely
 * unreachable -- these events are notifications for other services, not something our own
 * request/response cycle depends on. KafkaTemplate.send() can still throw SYNCHRONOUSLY (e.g. a
 * metadata-fetch timeout when no broker responds at all, see producer max.block.ms) and its
 * returned future can fail ASYNCHRONOUSLY -- this wraps both so neither ever breaks the caller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event)
                    .exceptionally(ex -> {
                        log.error("Kafka event publish failed: topic={} key={}", topic, key, ex);
                        return null;
                    });
        } catch (Exception ex) {
            log.error("Kafka event publish failed synchronously: topic={} key={}", topic, key, ex);
        }
    }
}
