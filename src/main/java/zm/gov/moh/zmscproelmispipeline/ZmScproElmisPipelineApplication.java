package zm.gov.moh.zmscproelmispipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@SpringBootApplication
@EnableKafkaStreams
public class ZmScproElmisPipelineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZmScproElmisPipelineApplication.class, args);
    }

}
