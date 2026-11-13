package cl.ochodigital.pasteleriamydreams.pedidosservice.controller;

import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Solicitud;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.SolicitudRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Solicitud de pedido de la Versión 1 (S2): formulario con el que el usuario V1
// pide sus delicias SIN comprar (no es una venta: no hay carrito, total ni
// código de seguimiento). El endpoint exige la sesión de Google con versión >= 1
// (igual que POST /api/pedidos exige versión >= 2), y además publica el evento
// SolicitudCreado en el topic `solicitudes` para que notificaciones-service avise
// por correo (misma forma de publicación que PedidoCreado: JSON por KafkaTemplate,
// best-effort tras persistir).
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {"solicitudes"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "app.kafka.topic.solicitudes=solicitudes"
})
class SolicitudIntegrationTest {

    private static final String TOPICO = "solicitudes";

    // Sesión de Google preautenticada de la usuaria V1 sembrada
    // (catherine.godoy.g@gmail.com): POST /api/solicitudes exige versión >= 1.
    // El mapeo real email -> autoridades VERSION_1..N se prueba en
    // AutenticacionJwtIntegrationTest.
    private static RequestPostProcessor sesionUsuarioV1() {
        return jwt().jwt(token -> token.claim("email", "catherine.godoy.g@gmail.com"))
                .authorities(new SimpleGrantedAuthority("VERSION_1"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SolicitudRepository solicitudRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    void solicitudValidaResponde201SeGuardaYPublicaElEvento() throws Exception {
        long antes = solicitudRepository.count();

        String respuesta = mockMvc.perform(post("/api/solicitudes").with(sesionUsuarioV1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Daniela Soto","email":"daniela@ejemplo.cl","telefono":"+56 9 8765 4321","detalle":"Torta de chocolate x1, Cupcakes vainilla x6","mensaje":"Para el sabado por la tarde"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Daniela Soto"))
                .andExpect(jsonPath("$.email").value("daniela@ejemplo.cl"))
                .andExpect(jsonPath("$.telefono").value("+56 9 8765 4321"))
                .andExpect(jsonPath("$.detalle").value("Torta de chocolate x1, Cupcakes vainilla x6"))
                .andExpect(jsonPath("$.mensaje").value("Para el sabado por la tarde"))
                .andExpect(jsonPath("$.fecha").exists())
                .andReturn().getResponse().getContentAsString();

        // 201 con el registro guardado: la fila existe en la base con los mismos datos
        assertEquals(antes + 1, solicitudRepository.count(), "La solicitud debió persistirse");
        long id = new ObjectMapper().readTree(respuesta).get("id").asLong();
        Optional<Solicitud> guardada = solicitudRepository.findById(id);
        assertTrue(guardada.isPresent(), "La solicitud debía existir en la base de datos");
        assertEquals("Daniela Soto", guardada.get().getNombre());
        assertEquals("Torta de chocolate x1, Cupcakes vainilla x6", guardada.get().getDetalle());

        // El evento viaja al topic con el evento y el id ya persistidos
        String mensaje = recibirEvento("daniela@ejemplo.cl", 10_000);
        assertTrue(mensaje.contains("SolicitudCreado"), "El mensaje no trae el evento: " + mensaje);
        assertTrue(mensaje.contains("\"id\":" + id), "El evento no trae el id persistido: " + mensaje);
    }

    // mensaje y telefono son opcionales: solo nombre, email y detalle son obligatorios
    @Test
    void solicitudSinTelefonoNiMensajeEsValida() throws Exception {
        long antes = solicitudRepository.count();

        mockMvc.perform(post("/api/solicitudes").with(sesionUsuarioV1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Catherine Godoy","email":"cat@ejemplo.cl","detalle":"Selva Negra x2"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Catherine Godoy"))
                .andExpect(jsonPath("$.detalle").value("Selva Negra x2"))
                .andExpect(jsonPath("$.telefono").value(nullValue()))
                .andExpect(jsonPath("$.mensaje").value(nullValue()));

        assertEquals(antes + 1, solicitudRepository.count());
    }

    // Regla del contrato: sin nombre, email o detalle (o con ellos en blanco)
    // responde 400 { "mensaje": ... } y NO se guarda ni se publica nada
    @Test
    void camposObligatoriosFaltantesResponden400SinPersistirNada() throws Exception {
        long antes = solicitudRepository.count();

        String[] cuerposInvalidos = {
                // sin nombre
                "{\"email\":\"rechazada@ejemplo.cl\",\"detalle\":\"Torta x1\"}",
                // nombre en blanco
                "{\"nombre\":\"  \",\"email\":\"rechazada@ejemplo.cl\",\"detalle\":\"Torta x1\"}",
                // sin email
                "{\"nombre\":\"Sin Email\",\"detalle\":\"Torta x1\"}",
                // sin detalle
                "{\"nombre\":\"Sin Detalle\",\"email\":\"rechazada@ejemplo.cl\"}",
                // detalle en blanco
                "{\"nombre\":\"Detalle Blanco\",\"email\":\"rechazada@ejemplo.cl\",\"detalle\":\" \"}",
        };
        for (String cuerpo : cuerposInvalidos) {
            mockMvc.perform(post("/api/solicitudes").with(sesionUsuarioV1())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").isNotEmpty());
        }

        // Nada quedó guardado y nada se publicó en el topic
        assertEquals(antes, solicitudRepository.count(),
                "Una solicitud rechazada no debe dejar filas en la base de datos");
        assertFalse(llegoEvento("rechazada@ejemplo.cl", 2_000),
                "Una solicitud rechazada no debe publicar eventos en el topic");
    }

    // Consume el topic hasta encontrar el evento que contiene el marcador (un dato
    // del payload, como el email) o agotar el tiempo máximo
    private String recibirEvento(String marcador, long timeoutMs) {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        propiedades.put(ConsumerConfig.GROUP_ID_CONFIG, "solicitud-assert-" + UUID.randomUUID());
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
                    if (registro.value() != null && registro.value().contains("SolicitudCreado")
                            && registro.value().contains(marcador)) {
                        return registro.value();
                    }
                }
            }
        }
        throw new AssertionError("No llegó el evento SolicitudCreado con '" + marcador + "' en " + timeoutMs + " ms");
    }

    // True si dentro del lapso llega algún evento que contenga el marcador
    private boolean llegoEvento(String marcador, long timeoutMs) {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        propiedades.put(ConsumerConfig.GROUP_ID_CONFIG, "solicitud-negativo-" + UUID.randomUUID());
        propiedades.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        propiedades.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        propiedades.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        propiedades.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConsumerFactory<String, String> fabrica = new DefaultKafkaConsumerFactory<>(propiedades);
        long limite = System.currentTimeMillis() + timeoutMs;
        List<String> vistos = new ArrayList<>();

        try (var consumidor = fabrica.createConsumer()) {
            consumidor.subscribe(List.of(TOPICO));
            while (System.currentTimeMillis() < limite) {
                for (ConsumerRecord<String, String> registro : consumidor.poll(Duration.ofMillis(500))) {
                    if (registro.value() != null) {
                        vistos.add(registro.value());
                    }
                }
            }
        }
        return vistos.stream().anyMatch(valor -> valor.contains(marcador));
    }
}
