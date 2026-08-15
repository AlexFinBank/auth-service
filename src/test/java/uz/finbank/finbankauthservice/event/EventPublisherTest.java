package uz.finbank.finbankauthservice.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private EventPublisher eventPublisher;

    private final Object event = new Object();

    @Test
    void publish_shouldSendThroughKafkaTemplate_onTheHappyPath() {
        eventPublisher = new EventPublisher(kafkaTemplate);
        when(kafkaTemplate.send(eq("topic"), eq("key"), any())).thenReturn(new CompletableFuture<>());

        eventPublisher.publish("topic", "key", event);

        verify(kafkaTemplate).send("topic", "key", event);
    }

    @Test
    void publish_shouldNotPropagate_whenKafkaTemplateThrowsSynchronously() {
        eventPublisher = new EventPublisher(kafkaTemplate);
        when(kafkaTemplate.send(eq("topic"), eq("key"), any()))
                .thenThrow(new org.apache.kafka.common.errors.TimeoutException("no broker reachable"));

        assertThatCode(() -> eventPublisher.publish("topic", "key", event)).doesNotThrowAnyException();
    }

    @Test
    void publish_shouldNotPropagate_whenTheReturnedFutureCompletesExceptionally() {
        eventPublisher = new EventPublisher(kafkaTemplate);
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("broker rejected the record"));
        when(kafkaTemplate.send(eq("topic"), eq("key"), any())).thenReturn(failedFuture);

        assertThatCode(() -> eventPublisher.publish("topic", "key", event)).doesNotThrowAnyException();
    }
}
