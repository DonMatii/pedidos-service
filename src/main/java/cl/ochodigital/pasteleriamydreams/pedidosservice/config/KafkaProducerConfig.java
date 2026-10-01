package cl.ochodigital.pasteleriamydreams.pedidosservice.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

// Configuración del productor de Kafka (RF-08 / RNF-08)
@Configuration
public class KafkaProducerConfig {

    private final String bootstrapServers;

    public KafkaProducerConfig(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    // Productor de texto con confirmación total: nada se pierde si el broker se cae
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configuracion = new HashMap<>();
        configuracion.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configuracion.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configuracion.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configuracion.put(ProducerConfig.ACKS_CONFIG, "all");
        configuracion.put(ProducerConfig.RETRIES_CONFIG, 10);
        configuracion.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configuracion.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);
        return new DefaultKafkaProducerFactory<>(configuracion);
    }

    // Plantilla que usa el servicio para enviar los eventos
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
