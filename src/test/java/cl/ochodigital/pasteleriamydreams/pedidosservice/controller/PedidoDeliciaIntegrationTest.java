package cl.ochodigital.pasteleriamydreams.pedidosservice.controller;

import cl.ochodigital.pasteleriamydreams.pedidosservice.client.DeliciaClient;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// S2: el pedido que incluye la Delicia del Día recibe -10% sobre el subtotal completo,
// una sola vez por transacción, acumulable al BIENVENIDO10 (persistido de verdad en H2)
@SpringBootTest
@AutoConfigureMockMvc
class PedidoDeliciaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PedidoRepository pedidoRepository;

    // El cliente de catálogo se mockea: los tests no dependen de catalogo-service levantado
    @MockBean
    private DeliciaClient deliciaClient;

    @BeforeEach
    void limpiar() {
        pedidoRepository.deleteAll();
    }

    @Test
    void pedidoConLaDeliciaDeHoyAplicaDiezPorCientoYLoPersiste() throws Exception {
        when(deliciaClient.obtenerNombreDelicia()).thenReturn(Optional.of("Torta Delicia"));

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoJson("Torta Delicia", null, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(30000))
                .andExpect(jsonPath("$.descuento").value(3000))
                .andExpect(jsonPath("$.total").value(27000));

        // El descuento quedó en la BD: `descuento` es la suma de ambos, sin cambios de esquema
        var guardado = pedidoRepository.findAll().get(0);
        assertEquals(30000, guardado.getTotal() + guardado.getDescuento());
        assertEquals(27000, guardado.getTotal().intValue());
        assertNull(guardado.getCodigoDescuento());

        // La consulta pública de seguimiento también lo refleja
        mockMvc.perform(get("/api/pedidos/seguimiento/{codigo}", guardado.getCodigoConsulta()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(27000))
                .andExpect(jsonPath("$.descuento").value(3000));
    }

    @Test
    void pedidoConDeliciaYCodigoBienvenidaAcumulaLosDosDescuentos() throws Exception {
        when(deliciaClient.obtenerNombreDelicia()).thenReturn(Optional.of("Torta Delicia"));

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoJson("Torta Delicia", "BIENVENIDO10", 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(30000))
                .andExpect(jsonPath("$.descuento").value(6000))
                .andExpect(jsonPath("$.total").value(24000))
                .andExpect(jsonPath("$.codigoDescuento").value("BIENVENIDO10"));
    }

    @Test
    void pedidoSinLaDeliciaDeHoyConservaElTotalCompleto() throws Exception {
        when(deliciaClient.obtenerNombreDelicia()).thenReturn(Optional.of("Torta Selva Negra"));

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoJson("Torta Delicia", null, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(15000))
                .andExpect(jsonPath("$.descuento").value(0))
                .andExpect(jsonPath("$.total").value(15000));
    }

    @Test
    void variasUnidadesDeLaDeliciaNoMultiplicanElDescuento() throws Exception {
        when(deliciaClient.obtenerNombreDelicia()).thenReturn(Optional.of("Torta Delicia"));

        // 4 unidades del destacado: el -10% es sobre el subtotal completo y una sola vez
        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoJson("Torta Delicia", null, 4)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(60000))
                .andExpect(jsonPath("$.descuento").value(6000))
                .andExpect(jsonPath("$.total").value(54000));
    }

    @Test
    void catalogoServiceCaidoElPedidoSeCreaSinDescuentoDeDelicia() throws Exception {
        // Fail-open: si la consulta a catalogo-service revienta, el pedido sigue adelante
        when(deliciaClient.obtenerNombreDelicia()).thenThrow(new RuntimeException("catalogo caído"));

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoJson("Torta Delicia", null, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(15000))
                .andExpect(jsonPath("$.descuento").value(0))
                .andExpect(jsonPath("$.total").value(15000));
    }

    @Test
    void nombreDeLaDeliciaConMayusculasYEspaciosExtraSigueMatcheando() throws Exception {
        when(deliciaClient.obtenerNombreDelicia()).thenReturn(Optional.of("  torta delicia  "));

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoJson("TORTA DELICIA ", null, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descuento").value(1500))
                .andExpect(jsonPath("$.total").value(13500));
    }

    // Pedido mínimo de un solo producto: cantidad x 15000 de precio unitario
    private String pedidoJson(String nombreProducto, String codigoDescuento, int cantidad) {
        String codigo = (codigoDescuento == null)
                ? ""
                : "\"codigoDescuento\":\"" + codigoDescuento + "\",";
        return "{\"cliente\":\"Cliente Delicia\",\"email\":\"delicia@ejemplo.cl\"," + codigo
                + "\"productos\":[{\"nombre\":\"" + nombreProducto + "\",\"cantidad\":" + cantidad
                + ",\"precioUnitario\":15000}]}";
    }
}
