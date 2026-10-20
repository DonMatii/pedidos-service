package cl.ochodigital.pasteleriamydreams.pedidosservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Pruebas del cliente HTTP hacia catalogo-service: ante 200 devuelve el nombre de la
// delicia; ante 404, 5xx o cualquier otra respuesta rara devuelve "sin delicia" (fail-open)
class DeliciaClientTest {

    private static final String BASE_URL = "http://catalogo.test";
    private static final String RUTA = BASE_URL + "/api/productos/delicia-del-dia";

    private MockRestServiceServer servidor;
    private DeliciaClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        // El servidor mock se engancha al builder ANTES de construir el RestClient
        servidor = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl(BASE_URL).build();
        cliente = new DeliciaClient(restClient, new ObjectMapper());
    }

    @Test
    void devuelveElNombreDeLaDeliciaCuandoCatalogoResponde200() {
        servidor.expect(requestTo(RUTA))
                .andRespond(withSuccess(
                        "{\"id\":3,\"nombre\":\"Torta Delicia\",\"categoria\":\"tortas\",\"precio\":12000}",
                        MediaType.APPLICATION_JSON));

        Optional<String> delicia = cliente.obtenerNombreDelicia();

        assertEquals(Optional.of("Torta Delicia"), delicia);
        servidor.verify();
    }

    @Test
    void devuelveSinDeliciaCuandoCatalogoResponde404() {
        servidor.expect(requestTo(RUTA))
                .andRespond(withResourceNotFound()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"mensaje\":\"No hay delicia de hoy\"}"));

        assertTrue(cliente.obtenerNombreDelicia().isEmpty());
        servidor.verify();
    }

    @Test
    void devuelveSinDeliciaCuandoCatalogoResponde500() {
        servidor.expect(requestTo(RUTA)).andRespond(withServerError());

        assertTrue(cliente.obtenerNombreDelicia().isEmpty());
        servidor.verify();
    }

    @Test
    void devuelveSinDeliciaCuandoLaRespuestaNoTraeElNombre() {
        servidor.expect(requestTo(RUTA))
                .andRespond(withSuccess("{\"algo\":\"otro shape\"}", MediaType.APPLICATION_JSON));

        assertTrue(cliente.obtenerNombreDelicia().isEmpty());
        servidor.verify();
    }
}
