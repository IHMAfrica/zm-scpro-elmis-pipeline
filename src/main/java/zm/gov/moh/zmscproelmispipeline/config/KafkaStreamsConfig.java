package zm.gov.moh.zmscproelmispipeline.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaStreamsConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.application.name}")
    private String applicationId;

    @Value("${spring.kafka.properties.sasl.jaas.config}")
    private String jaasConfig;

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kStreamsConfig() {
        Map<String, Object> props = new HashMap<>();

        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
        props.put("sasl.mechanism", "SCRAM-SHA-256");
        props.put("sasl.jaas.config", jaasConfig);

        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 2);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100);

        props.put(StreamsConfig.producerPrefix(org.apache.kafka.clients.producer.ProducerConfig.LINGER_MS_CONFIG), 10);
        props.put(StreamsConfig.producerPrefix(org.apache.kafka.clients.producer.ProducerConfig.BATCH_SIZE_CONFIG), 32768);
        props.put(StreamsConfig.producerPrefix(org.apache.kafka.clients.producer.ProducerConfig.COMPRESSION_TYPE_CONFIG), "lz4");
        props.put(StreamsConfig.producerPrefix(org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG), "1");
        props.put(StreamsConfig.producerPrefix(org.apache.kafka.clients.producer.ProducerConfig.BUFFER_MEMORY_CONFIG), 67108864L); // 64MB

        props.put(StreamsConfig.consumerPrefix(ConsumerConfig.FETCH_MIN_BYTES_CONFIG), 1024);
        props.put(StreamsConfig.consumerPrefix(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG), 500);
        props.put(StreamsConfig.consumerPrefix(ConsumerConfig.MAX_POLL_RECORDS_CONFIG), 500);
        props.put(StreamsConfig.consumerPrefix(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG), 30000);

        return new KafkaStreamsConfiguration(props);
    }

    @Bean
    public AdminClient adminClient() {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 10000);
        config.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
        config.put("sasl.mechanism", "SCRAM-SHA-256");
        config.put("sasl.jaas.config", jaasConfig);

        return AdminClient.create(config);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
