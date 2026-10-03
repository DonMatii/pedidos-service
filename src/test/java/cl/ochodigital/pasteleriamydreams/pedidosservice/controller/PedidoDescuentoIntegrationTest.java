package cl.ochodigital.pasteleriamydreams.pedidosservice.controller;

import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// RF extra (feedback del profe): descuento de bienvenida -10% en la primera compra,
// persistido de verdad en la BD (no un descuento solo de frontend)
@SpringBootTest
@AutoConfigureMockMvc
class PedidoDescuentoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PedidoRepository pedidoRepository;

    @BeforeEach
    void limpiar() {
        pedidoRepository.deleteAll();
    }

    @Test
    void pedidoConCodigoBienvenidaAplicaDiezPorCientoYLoPersiste() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"Primera Vez\",\"email\":\"primera@ejemplo.cl\","
                                + "\"codigoDescuento\":\"BIENVENIDO10\","
                                + "\"productos\":[{\"nombre\":\"Torta\",\"cantidad\":2,\"precioUnitario\":15000}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(30000))
                .andExpect(jsonPath("$.descuento").value(3000))
                .andExpect(jsonPath("$.total").value(27000))
                .andExpect(jsonPath("$.codigoDescuento").value("BIENVENIDO10"));

        // El descuento quedo en la BD, no solo en la respuesta
        var guardado = pedidoRepository.findAll().get(0);
        assertEquals(30000, guardado.getTotal() + guardado.getDescuento());
        assertEquals(27000, guardado.getTotal().intValue());
        assertEquals("BIENVENIDO10", guardado.getCodigoDescuento());

        // La consulta publica de seguimiento tambien lo refleja
        mockMvc.perform(get("/api/pedidos/seguimiento/{codigo}", guardado.getCodigoConsulta()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(27000))
                .andExpect(jsonPath("$.descuento").value(3000));
    }

    @Test
    void pedidoConCodigoDesconocidoResponde400() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"Vuelve\",\"email\":\"vuelve@ejemplo.cl\","
                                + "\"codigoDescuento\":\"AHORRO50\","
                                + "\"productos\":[{\"nombre\":\"Torta\",\"cantidad\":1,\"precioUnitario\":10000}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Código de descuento inválido: AHORRO50"));
    }

    @Test
    void pedidoSinCodigoConservaElTotalCompleto() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"Sin Codigo\",\"email\":\"sincodigo@ejemplo.cl\","
                                + "\"productos\":[{\"nombre\":\"Torta\",\"cantidad\":2,\"precioUnitario\":15000}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(30000))
                .andExpect(jsonPath("$.descuento").value(0))
                .andExpect(jsonPath("$.total").value(30000));
    }
}
