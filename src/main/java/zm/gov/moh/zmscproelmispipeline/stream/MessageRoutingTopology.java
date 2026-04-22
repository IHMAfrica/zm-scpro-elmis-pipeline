package zm.gov.moh.zmscproelmispipeline.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import zm.gov.moh.zmscproelmispipeline.dto.DispensationPayload;
import zm.gov.moh.zmscproelmispipeline.dto.DispensedDrug;
import zm.gov.moh.zmscproelmispipeline.dto.MessagePayload;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageRoutingTopology {
    private final ObjectMapper objectMapper;
    @Value("${spring.kafka.topics.prescriptions}")
    private String prescriptionTopic;

    @Value("${spring.kafka.topics.patient-profiles}")
    private String patientProfileTopic;

    @Value("${spring.kafka.topics.dispensation-acks}")
    private String dispensationAckTopic;

    @Value("${spring.kafka.topics.dispensation}")
    private String dispensationTopic;

    @Value("${spring.kafka.topics.dispensation-prime}")
    private String dispensationPrimeTopic;

    @Value("${spring.kafka.dlq.topic}")
    private String dlqTopic;

    private static final DateTimeFormatter MSH_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern VALID_TOPIC_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private static final Pattern INVALID_CHARS_PATTERN = Pattern.compile("[^a-zA-Z0-9._-]");

    private static final int MAX_TOPIC_LENGTH = 249;

    @Bean
    public KStream<String, String> prescriptionRoutingStream(StreamsBuilder streamsBuilder) {
        return stream(streamsBuilder, prescriptionTopic, "prescription-consumer");
    }

    @Bean
    public KStream<String, String> patientProfileRoutingStream(StreamsBuilder streamsBuilder) {
        return stream(streamsBuilder, patientProfileTopic, "patient-profile-consumer");
    }

    @Bean
    public KStream<String, String> dispensationAckRoutingStream(StreamsBuilder streamsBuilder) {
        return stream(streamsBuilder, dispensationAckTopic, "dispensation-ack-consumer");
    }

    @Bean
    public KStream<String, String> prescriptionDfzRoutingStream(StreamsBuilder streamsBuilder) {
        return stream(streamsBuilder, "dfz-" + prescriptionTopic, "prescription-dfz-consumer");
    }

    @Bean
    public KStream<String, String> patientProfileDfzRoutingStream(StreamsBuilder streamsBuilder) {
        return stream(streamsBuilder, "dfz-" + patientProfileTopic, "patient-profile-dfz-consumer");
    }

    @Bean
    public KStream<String, String> dispensationDfzAckRoutingStream(StreamsBuilder streamsBuilder) {
        return stream(streamsBuilder, "dfz" + dispensationAckTopic, "dispensation-ack-dfz-consumer");
    }

    @Bean
    public KStream<String, String> dispensationPrimeStream(StreamsBuilder streamsBuilder) {
        KStream<String, String> sourceStream = streamsBuilder
                .stream(
                        dispensationTopic,
                        Consumed.with(Serdes.String(), Serdes.String())
                                .withName("dispensation-consumer")
                                .withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST)
                );

        sourceStream
                .filter((_, messageJson) -> isEligibleForPrime(messageJson))
                .to(dispensationPrimeTopic, Produced.with(Serdes.String(), Serdes.String()));

        return sourceStream;
    }

    private KStream<String, String> stream(StreamsBuilder streamsBuilder, String topic, String processorName) {
        KStream<String, String> sourceStream = streamsBuilder
                .stream(
                        topic,
                        Consumed.with(Serdes.String(), Serdes.String())
                                .withName(processorName)
                                .withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST)
                );

        sourceStream
                .map((_, messageJson) -> routeMessage(messageJson))
                .filter((_, routedMessage) -> routedMessage != null)
                .to((targetTopic, _, _) -> targetTopic, Produced.with(Serdes.String(), Serdes.String()));

        return sourceStream;
    }

    private KeyValue<String, String> routeMessage(String messageJson) {
        long startTime = System.nanoTime();

        try {
            if (messageJson == null || messageJson.trim().isEmpty()) {
                log.warn("❌ Received null or empty message, sending to DLQ");
                return sendToDlq(messageJson, "Empty message");
            }

            MessagePayload payload;
            try {
                payload = objectMapper.readValue(messageJson, MessagePayload.class);
            } catch (Exception e) {
                log.error("JSON parsing failed: {} - Sending to DLQ", e.getMessage());
                log.debug("Failed message: {}", messageJson.substring(0, Math.min(200, messageJson.length())));
                return sendToDlq(messageJson, "JSON parse error: " + e.getMessage());
            }

            if (payload == null || payload.getMsh() == null) {
                log.error("❌ Message missing 'msh' header - Sending to DLQ");
                return sendToDlq(messageJson, "Missing msh header");
            }

            String rawHmisCode = payload.getMsh().getHmisCode();
            String messageId = payload.getMsh().getMessageId();

            if (rawHmisCode == null || rawHmisCode.trim().isEmpty()) {
                log.error("❌ Message {} missing 'hmisCode' - Sending to DLQ", messageId);
                return sendToDlq(messageJson, "Missing hmisCode");
            }

            String sanitizedHmisCode = sanitizeHmisCode(rawHmisCode);

            if (sanitizedHmisCode == null || sanitizedHmisCode.isEmpty()) {
                log.error("❌ Message {} has invalid hmisCode '{}' (cannot be sanitized) - Sending to DLQ",
                        messageId, rawHmisCode);
                return sendToDlq(messageJson, "Invalid hmisCode: " + rawHmisCode);
            }

            if (!rawHmisCode.equals(sanitizedHmisCode)) {
                log.warn("⚠️ Sanitized hmisCode: '{}' → '{}' for message {}",
                        rawHmisCode, sanitizedHmisCode, messageId);
            }

            String targetTopic = generateTopicName(sanitizedHmisCode);

            if (!isValidTopicName(targetTopic)) {
                log.error("❌ Generated invalid topic name '{}' from hmisCode '{}' - Sending to DLQ",
                        targetTopic, sanitizedHmisCode);
                return sendToDlq(messageJson, "Invalid topic name: " + targetTopic);
            }

            long parseTime = (System.nanoTime() - startTime) / 1_000_000;

            if (parseTime > 10) {
                log.warn("⚠️ Slow processing: {}ms for message {}", parseTime, messageId);
            }

            return KeyValue.pair(targetTopic, messageJson);

        } catch (Exception e) {
            log.error("❌ Unexpected error routing message: {}", e.getMessage(), e);
            return sendToDlq(messageJson, "Unexpected error: " + e.getMessage());
        }
    }

    private boolean isEligibleForPrime(String messageJson) {
        try {
            DispensationPayload payload = objectMapper.readValue(messageJson, DispensationPayload.class);

            if (payload == null || payload.getMsh() == null) {
                log.warn("Dispensation message missing msh header, skipping prime routing");
                return false;
            }

            String timestampStr = payload.getMsh().getTimestamp();
            if (timestampStr == null || timestampStr.trim().isEmpty()) {
                log.warn("Dispensation message {} missing msh timestamp, skipping prime routing",
                        payload.getMsh().getMessageId());
                return false;
            }

            LocalDateTime messageTime = LocalDateTime.parse(timestampStr.trim(), MSH_TIMESTAMP_FORMAT);
            if (messageTime.isBefore(LocalDateTime.now().minusHours(24))) {
                log.warn("Dispensation message {} timestamp {} is older than 24 hours, skipping prime routing",
                        payload.getMsh().getMessageId(), timestampStr);
                return false;
            }

            List<DispensedDrug> drugs = payload.getDispensedDrugs();
            if (drugs == null || drugs.isEmpty()) {
                log.warn("Dispensation message {} has no dispensed drugs, skipping prime routing",
                        payload.getMsh().getMessageId());
                return false;
            }

            boolean hasValidMedicationId = drugs.stream()
                    .anyMatch(drug -> drug.getMedicationId() != null && !drug.getMedicationId().trim().isEmpty());

            if (!hasValidMedicationId) {
                log.warn("Dispensation message {} has no drug with a non-null medicationId, skipping prime routing",
                        payload.getMsh().getMessageId());
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("Failed to evaluate dispensation message for prime routing: {}", e.getMessage());
            return false;
        }
    }

    private String sanitizeHmisCode(String rawHmisCode) {
        if (rawHmisCode == null) {
            return null;
        }

        String sanitized = INVALID_CHARS_PATTERN.matcher(rawHmisCode).replaceAll("_");

        sanitized = sanitized.replaceAll("_+", "_");

        sanitized = sanitized.replaceAll("^[._]+|[._]+$", "");

        sanitized = sanitized.trim();

        return sanitized.isEmpty() ? null : sanitized;
    }

    private boolean isValidTopicName(String topicName) {
        if (topicName == null || topicName.isEmpty()) {
            return false;
        }

        if (topicName.length() > MAX_TOPIC_LENGTH) {
            log.warn("Topic name '{}' exceeds max length of {}", topicName, MAX_TOPIC_LENGTH);
            return false;
        }

        if (!VALID_TOPIC_PATTERN.matcher(topicName).matches()) {
            log.warn("Topic name '{}' contains invalid characters", topicName);
            return false;
        }

        if (topicName.startsWith(".") || topicName.startsWith("_")) {
            log.warn("Topic name '{}' starts with invalid character", topicName);
            return false;
        }

        return true;
    }

    private String generateTopicName(String sanitizedHmisCode) {
        return String.format("h-%s_m-PR", sanitizedHmisCode);
    }

    private KeyValue<String, String> sendToDlq(String messageJson, String errorReason) {
        log.warn("📮 Sending to DLQ: {}", errorReason);

        String dlqMessage = String.format("{\"error\":\"%s\",\"originalMessage\":%s}",
                errorReason.replace("\"", "'"),
                messageJson != null ? messageJson : "null");

        return KeyValue.pair(dlqTopic, dlqMessage);
    }
}
