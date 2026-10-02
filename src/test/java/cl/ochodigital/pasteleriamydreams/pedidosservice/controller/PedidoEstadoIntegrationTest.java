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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Verifica RF-11: el PATCH /{id}/estado cambia y persiste el estado sobre H2 (sin broker)
@SpringBootTest
@AutoConfigureMockMvc
class PedidoEstadoIntegrationTest {

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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ENTREGADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"));

        // El estado quedó persistido: la consulta lo devuelve igual
        mockMvc.perform(get("/api/pedidos/{id}", idPedido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"));
    }

    @Test
    void cambiarEstadoResponde400ConMensajeSiElEstadoEsInvalido() throws Exception {
        mockMvc.perform(patch("/api/pedidos/{id}/estado", idPedido)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CANCELADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Estado invalido: CANCELADO"));
    }

    @Test
    void cambiarEstadoResponde404SiElPedidoNoExiste() throws Exception {
        mockMvc.perform(patch("/api/pedidos/{id}/estado", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ENTREGADO\"}"))
                .andExpect(status().isNotFound());
    }
}
