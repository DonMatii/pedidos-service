package cl.ochodigital.pasteleriamydreams.pedidosservice.service;

import cl.ochodigital.pasteleriamydreams.pedidosservice.client.DeliciaClient;
import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Pedido;
import cl.ochodigital.pasteleriamydreams.pedidosservice.model.PedidoItem;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.PedidoRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Verifica S1/S2: cambiar de estado publica PedidoEstadoCambiado en el topic
// PROPIO `pedidos-estados`. Topic aparte porque Notificacion.pedidoId es unique
// y la idempotencia del listener de PedidoCreado descartaria cualquier segundo
// evento del mismo pedido.
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"pedidos", "pedidos-estados"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "app.kafka.topic=pedidos",
        "app.kafka.topic.estados=pedidos-estados"
})
class PedidoEstadoPublicacionIntegrationTest {

    private static final String TOPICO_ESTADOS = "pedidos-estados";
    private static final String GRUPO_CONSUMIDOR = "test-assert-estados";
    private static final String CODIGO_SEGUIMIENTO = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4";

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    // El cliente de catalogo se mockea vacio: esta prueba verifica solo Kafka
    @MockBean
    private DeliciaClient deliciaClient;

    private Long idPedido;

    @BeforeEach
    void preparar() {
        pedidoRepository.deleteAll();
        Pedido pedido = new Pedido();
        pedido.setCliente("Daniela Soto");
        pedido.setEmail("daniela@ejemplo.cl");
        pedido.setTotal(33990);
        pedido.setCodigoConsulta(CODIGO_SEGUIMIENTO);

        PedidoItem torta = new PedidoItem();
        torta.setNombre("Torta de chocolate");
        torta.setCantidad(1);
        torta.setPrecioUnitario(18990);
        torta.setPedido(pedido);
        pedido.getItems().add(torta);

        PedidoItem alfajor = new PedidoItem();
        alfajor.setNombre("Alfajor");
        alfajor.setCantidad(3);
        alfajor.setPrecioUnitario(5000);
        alfajor.setPedido(pedido);
        pedido.getItems().add(alfajor);

        // La entidad arranca en RECIBIDO por defecto
        idPedido = pedidoRepository.save(pedido).getId();
    }

    @Test
    void cambiarEstadoPublicaElEventoConElEstadoAnteriorYElNuevo() {
        String mensaje = null;
        pedidoService.cambiarEstado(idPedido, "ENTREGADO");

        mensaje = recibirEvento(10_000);

        assertTrue(mensaje.contains("PedidoEstadoCambiado"),
                "El evento debio identificarse como PedidoEstadoCambiado: " + mensaje);
        assertTrue(mensaje.contains("\"id\":" + idPedido),
                "El evento debio traer el id del pedido: " + mensaje);
        assertTrue(mensaje.contains("\"estadoAnterior\":\"RECIBIDO\""),
                "El estado anterior debio ser RECIBIDO: " + mensaje);
        assertTrue(mensaje.contains("\"estadoNuevo\":\"ENTREGADO\""),
                "El estado nuevo debio ser ENTREGADO: " + mensaje);
        assertTrue(mensaje.contains("Daniela Soto"), "Debio traer el cliente: " + mensaje);
        assertTrue(mensaje.contains("daniela@ejemplo.cl"), "Debio traer el email: " + mensaje);
        assertTrue(mensaje.contains(CODIGO_SEGUIMIENTO),
                "Debio traer el codigo de seguimiento para el enlace del correo: " + mensaje);
        assertTrue(mensaje.contains("Torta de chocolate"),
                "Debio traer los productos del pedido: " + mensaje);
        assertTrue(mensaje.contains("Alfajor"),
                "Debio traer los productos del pedido: " + mensaje);
    }

    @Test
    void guardarElMismoEstadoNoPublicaEvento() {
        // El pedido ya esta en RECIBIDO: pedir RECIBIDO de nuevo no es un cambio
        pedidoService.cambiarEstado(idPedido, "RECIBIDO");

        assertThrows(AssertionError.class, () -> recibirEvento(2_000),
                "No debio llegar evento si el estado no cambio: no debe repetir el correo");
    }

    @Test
    void elCicloCompletoPublicaUnEventoPorCadaSalto() {
        pedidoService.cambiarEstado(idPedido, "EN_PREPARACION");
        String primero = recibirEvento(10_000);
        assertTrue(primero.contains("\"estadoNuevo\":\"EN_PREPARACION\""), primero);
        assertTrue(primero.contains("\"estadoAnterior\":\"RECIBIDO\""), primero);

        pedidoService.cambiarEstado(idPedido, "DESPACHADO");
        String segundo = recibirEvento(10_000);
        assertTrue(segundo.contains("\"estadoNuevo\":\"DESPACHADO\""), segundo);
        assertTrue(segundo.contains("\"estadoAnterior\":\"EN_PREPARACION\""), segundo);
    }

    // Consume el topic de estados hasta encontrar el evento o agotar el tiempo
    private String recibirEvento(long timeoutMs) {
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
            consumidor.subscribe(List.of(TOPICO_ESTADOS));
            while (System.currentTimeMillis() < limite) {
                for (ConsumerRecord<String, String> registro : consumidor.poll(Duration.ofMillis(500))) {
                    if (registro.value() != null && registro.value().contains("PedidoEstadoCambiado")) {
                        return registro.value();
                    }
                }
            }
        }
        throw new AssertionError("No llego el evento PedidoEstadoCambiado en " + timeoutMs + " ms");
    }
}
