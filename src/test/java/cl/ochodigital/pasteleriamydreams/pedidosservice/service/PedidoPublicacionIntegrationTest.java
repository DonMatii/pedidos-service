package cl.ochodigital.pasteleriamydreams.pedidosservice.service;

import cl.ochodigital.pasteleriamydreams.pedidosservice.dto.PedidoRequest;
import cl.ochodigital.pasteleriamydreams.pedidosservice.dto.PedidoResponse;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.PedidoRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Verifica RNF-08: el pedido se persiste primero y recién después se publica en Kafka
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"pedidos"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "app.kafka.topic=pedidos"
})
class PedidoPublicacionIntegrationTest {

    private static final String TOPICO = "pedidos";
    private static final String GRUPO_CONSUMIDOR = "test-assert";

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    void persisteElPedidoAntesDePublicarElEvento() {
        // 1) Registrar el pedido dispara persistencia + publicación
        PedidoResponse respuesta = pedidoService.registrar(solicitudValida());

        assertNotNull(respuesta.getId());
        assertTrue(respuesta.isEventoPublicado(), "El broker debió confirmar la publicación");

        // 2) El pedido quedó guardado en la base de datos
        assertTrue(pedidoRepository.findById(respuesta.getId()).isPresent(),
                "El pedido debía existir en la base de datos");

        // 3) Llega el evento y traer exactamente el mismo id: se publicó con el id ya generado
        String mensaje = recibirEventoPedidoCreado(10_000);
        assertTrue(mensaje.contains("PedidoCreado"), "El mensaje no trae el evento: " + mensaje);
        assertTrue(mensaje.contains("\"id\":" + respuesta.getId()),
                "El evento no trae el id persistido: " + mensaje);
        assertEquals(33990, respuesta.getTotal());
    }

    // Consume el topic hasta encontrar el evento o agotar el tiempo máximo
    private String recibirEventoPedidoCreado(long timeoutMs) {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        propiedades.put(ConsumerConfig.GROUP_ID_CONFIG, GRUPO_CONSUMIDOR);
        propiedades.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        propiedades.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        propiedades.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        propiedades.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConsumerFactory<String, String> fabrica = new DefaultKafkaConsumerFactory<>(propiedades);
        long limite = System.currentTimeMillis() + timeoutMs;

        try (var consumidor = fabrica.createConsumer()) {
            consumidor.subscribe(List.of(TOPICO));
            while (System.currentTimeMillis() < limite) {
                for (ConsumerRecord<String, String> registro : consumidor.poll(Duration.ofMillis(500))) {
                    if (registro.value() != null && registro.value().contains("PedidoCreado")) {
                        return registro.value();
                    }
                }
            }
        }
        throw new AssertionError("No llegó el evento PedidoCreado en " + timeoutMs + " ms");
    }

    // Solicitud válida de ejemplo: 1 torta + 6 cupcakes
    private PedidoRequest solicitudValida() {
        PedidoRequest.ProductoRequest torta = new PedidoRequest.ProductoRequest();
        torta.setNombre("Torta de chocolate");
        torta.setCantidad(1);
        torta.setPrecioUnitario(18990);

        PedidoRequest.ProductoRequest cupcakes = new PedidoRequest.ProductoRequest();
        cupcakes.setNombre("Cupcakes vainilla");
        cupcakes.setCantidad(6);
        cupcakes.setPrecioUnitario(2500);

        PedidoRequest solicitud = new PedidoRequest();
        solicitud.setCliente("Daniela Soto");
        solicitud.setEmail("daniela@ejemplo.cl");
        solicitud.setProductos(new ArrayList<>(List.of(torta, cupcakes)));
        return solicitud;
    }
}
