package cl.ochodigital.pasteleriamydreams.pedidosservice.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Optional;

// Cliente HTTP hacia catalogo-service: consulta cuál es la Delicia del Día de hoy.
// Política fail-open: 404 (no hay delicia), cualquier otro 4xx/5xx, sin conexión o
// timeout devuelven "sin delicia" con log warning; NUNCA bloquea la creación del pedido.
@Component
public class DeliciaClient {

    private static final Logger log = LoggerFactory.getLogger(DeliciaClient.class);

    private static final String RUTA_DELICIA = "/api/productos/delicia-del-dia";

    // Timeout corto a propósito: un pedido no puede demorarse esperando al catálogo
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    // @Autowired: hay dos constructores, Spring debe saber cuál usar para el bean real
    // (el constructor package-private es solo para tests con MockRestServiceServer)
    @Autowired
    public DeliciaClient(RestClient.Builder builder,
                         // El default aplica solo en tests: src/test/resources/application.properties
                         // reemplaza al archivo principal y no define esta propiedad
                         @Value("${app.catalogo.base-url:http://localhost:8080}") String baseUrl,
                         ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
    }

    // Variante para tests: recibe un RestClient ya construido para poder interceptarlo
    // con MockRestServiceServer (bindTo solo acepta RestClient.Builder en Spring 6.1)
    DeliciaClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    // Nombre del producto destacado de hoy, o vacío si no hay delicia o el catálogo no responde
    public Optional<String> obtenerNombreDelicia() {
        try {
            String cuerpo = restClient.get().uri(RUTA_DELICIA).retrieve().body(String.class);
            return extraerNombre(cuerpo);
        } catch (RestClientResponseException e) {
            // 404 = hoy no hay delicia; cualquier otro 4xx/5xx = catálogo no disponible
            log.warn("catalogo-service respondió {} en {}: el pedido se procesa sin descuento de delicia",
                    e.getStatusCode(), RUTA_DELICIA);
            return Optional.empty();
        } catch (ResourceAccessException e) {
            log.warn("No se pudo contactar a catalogo-service ({}): el pedido se procesa sin descuento de delicia",
                    e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Respuesta inesperada de catalogo-service ({}): el pedido se procesa sin descuento de delicia",
                    e.getMessage());
            return Optional.empty();
        }
    }

    // Extrae el campo "nombre" del JSON del producto (mismo shape que el resto del catálogo).
    // Un JSON ilegible propaga la excepción al try principal, que lo trata como "sin delicia"
    private Optional<String> extraerNombre(String cuerpo) throws JsonProcessingException {
        if (cuerpo == null || cuerpo.isBlank()) {
            return Optional.empty();
        }
        JsonNode nombre = objectMapper.readTree(cuerpo).get("nombre");
        if (nombre == null || nombre.isNull() || nombre.asText().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(nombre.asText().trim());
    }
}
