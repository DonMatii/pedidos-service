package cl.ochodigital.pasteleriamydreams.pedidosservice.controller;

import cl.ochodigital.pasteleriamydreams.pedidosservice.client.DeliciaClient;
import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Pedido;
import cl.ochodigital.pasteleriamydreams.pedidosservice.model.PedidoItem;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// S3: el dashboard de administracion necesita ver que se vendio (producto, unidades y
// monto) por rango de fechas. Agrupa los items de los pedidos reales sobre H2
@SpringBootTest
@AutoConfigureMockMvc
class PedidoVentasPorProductoIntegrationTest {

    private static final String API_KEY = "test-admin-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PedidoRepository pedidoRepository;

    @MockBean
    private DeliciaClient deliciaClient;

    @BeforeEach
    void preparar() {
        pedidoRepository.deleteAll();
        LocalDate hoy = LocalDate.now();

        Pedido hoy1 = new Pedido();
        hoy1.setCliente("Ana Perez");
        hoy1.setEmail("ana@ejemplo.cl");
        hoy1.setTotal(100000);
        hoy1.setFecha(hoy.atTime(10, 0));
        hoy1.getItems().add(item(hoy1, "Torta Selva Negra", 2, 25000));
        hoy1.getItems().add(item(hoy1, "Muffins Plátano Arándano", 6, 5000));
        pedidoRepository.save(hoy1);

        Pedido hoy2 = new Pedido();
        hoy2.setCliente("Luis Rojas");
        hoy2.setEmail("luis@ejemplo.cl");
        hoy2.setTotal(37000);
        hoy2.setFecha(hoy.atTime(15, 0));
        hoy2.getItems().add(item(hoy2, "Torta Selva Negra", 1, 25000));
        hoy2.getItems().add(item(hoy2, "Tarta de Fruta", 1, 12000));
        pedidoRepository.save(hoy2);

        Pedido ayer = new Pedido();
        ayer.setCliente("Carla Munoz");
        ayer.setEmail("carla@ejemplo.cl");
        ayer.setTotal(125000);
        ayer.setFecha(hoy.minusDays(1).atTime(10, 0));
        ayer.getItems().add(item(ayer, "Torta Moka", 5, 25000));
        pedidoRepository.save(ayer);
    }

    @Test
    void agrupaLasUnidadesVendidasPorProductoEnElRango() throws Exception {
        LocalDate hoy = LocalDate.now();

        mockMvc.perform(get("/api/pedidos/estadisticas/productos")
                        .param("desde", hoy.toString())
                        .param("hasta", hoy.toString())
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nombre").value("Muffins Plátano Arándano"))
                .andExpect(jsonPath("$[0].unidades").value(6))
                .andExpect(jsonPath("$[0].monto").value(30000))
                .andExpect(jsonPath("$[1].nombre").value("Torta Selva Negra"))
                .andExpect(jsonPath("$[1].unidades").value(3))
                .andExpect(jsonPath("$[1].monto").value(75000))
                .andExpect(jsonPath("$[2].nombre").value("Tarta de Fruta"))
                .andExpect(jsonPath("$[2].unidades").value(1));
    }

    @Test
    void responde401SinApiKey() throws Exception {
        LocalDate hoy = LocalDate.now();

        mockMvc.perform(get("/api/pedidos/estadisticas/productos")
                        .param("desde", hoy.toString())
                        .param("hasta", hoy.toString()))
                .andExpect(status().isUnauthorized());
    }

    private PedidoItem item(Pedido pedido, String nombre, int cantidad, int precio) {
        PedidoItem i = new PedidoItem();
        i.setNombre(nombre);
        i.setCantidad(cantidad);
        i.setPrecioUnitario(precio);
        i.setPedido(pedido);
        return i;
    }
}
