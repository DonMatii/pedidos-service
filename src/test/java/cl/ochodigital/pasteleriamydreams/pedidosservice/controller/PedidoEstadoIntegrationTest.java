package cl.ochodigital.pasteleriamydreams.pedidosservice.controller;

import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Pedido;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Verifica RF-11: el PATCH /{id}/estado cambia y persiste el estado sobre H2 (sin broker),
// ademas de la seguridad (API key en endpoints administrativos y consulta por codigo opaco)
@SpringBootTest
@AutoConfigureMockMvc
class PedidoEstadoIntegrationTest {

    // Misma key que define src/test/resources/application.properties
    private static final String API_KEY = "test-admin-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PedidoRepository pedidoRepository;

    private Long idPedido;

    @BeforeEach
    void preparar() {
        pedidoRepository.deleteAll();
        Pedido pedido = new Pedido();
        pedido.setCliente("Daniela Soto");
        pedido.setEmail("daniela@ejemplo.cl");
        pedido.setTotal(18990);
        idPedido = pedidoRepository.save(pedido).getId();
    }

    @Test
    void cambiarEstadoResponde200YElGetReflejaElNuevoEstado() throws Exception {
        mockMvc.perform(patch("/api/pedidos/{id}/estado", idPedido)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ENTREGADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"));

        // El estado quedó persistido: la consulta lo devuelve igual
        mockMvc.perform(get("/api/pedidos/{id}", idPedido)
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"));
    }

    @Test
    void cambiarEstadoResponde400ConMensajeSiElEstadoEsInvalido() throws Exception {
        mockMvc.perform(patch("/api/pedidos/{id}/estado", idPedido)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CANCELADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Estado invalido: CANCELADO"));
    }

    @Test
    void cambiarEstadoResponde404SiElPedidoNoExiste() throws Exception {
        mockMvc.perform(patch("/api/pedidos/{id}/estado", 999999L)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ENTREGADO\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void endpointsAdministrativosResponden401SinApiKey() throws Exception {
        mockMvc.perform(patch("/api/pedidos/{id}/estado", idPedido)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ENTREGADO\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/pedidos"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/pedidos/{id}", idPedido))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointsAdministrativosResponden401ConApiKeyIncorrecta() throws Exception {
        mockMvc.perform(get("/api/pedidos")
                        .header("X-Api-Key", "clave-equivocada"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consultaPorCodigoOpacoResponde200Y404SinExponerIds() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setCliente("Catherine Godoy");
        pedido.setEmail("catherine@ejemplo.cl");
        pedido.setTotal(120000);
        pedido.setCodigoConsulta("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4");
        pedidoRepository.save(pedido);

        // El codigo opaco encuentra el pedido...
        mockMvc.perform(get("/api/pedidos/seguimiento/{codigo}", "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoConsulta").value("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"))
                .andExpect(jsonPath("$.cliente").value("Catherine Godoy"));

        // ...y un codigo inexistente responde 404 sin adivinar ids
        mockMvc.perform(get("/api/pedidos/seguimiento/{codigo}", "no-existe-000"))
                .andExpect(status().isNotFound());

        // La consulta por codigo es publica: no exige API key
        mockMvc.perform(get("/api/pedidos/seguimiento/{codigo}", "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"))
                .andExpect(status().isOk());
    }

    @Test
    void creacionDePedidoSigueSiendoPublicaSinApiKey() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"Pedro Prueba\",\"email\":\"pedro@ejemplo.cl\","
                                + "\"productos\":[{\"nombre\":\"Torta\",\"cantidad\":1,\"precioUnitario\":10000}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoConsulta").isNotEmpty());
    }
}
