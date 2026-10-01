package cl.ochodigital.pasteleriamydreams.pedidosservice.service;

import cl.ochodigital.pasteleriamydreams.pedidosservice.dto.PedidoRequest;
import cl.ochodigital.pasteleriamydreams.pedidosservice.dto.PedidoResponse;
import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Pedido;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.PedidoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Pruebas unitarias del servicio de pedidos (repositorio y Kafka simulados, sin H2 ni broker)
class PedidoServiceTest {

    private PedidoRepository pedidoRepository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private PedidoService pedidoService;

    @BeforeEach
    void preparar() {
        pedidoRepository = mock(PedidoRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        pedidoService = new PedidoService(pedidoRepository, kafkaTemplate, objectMapper, "pedidos");

        // El broker confirma la publicación en los casos donde el pedido es válido
        when(kafkaTemplate.send(anyString(), anyString()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));
    }

    @Test
    void registrarCalculaElTotalSumandoTodosLosItems() {
        alPersistirAsignarId(7L);

        PedidoResponse respuesta = pedidoService.registrar(solicitudValida());

        assertEquals(33990, respuesta.getTotal());
        assertEquals(7L, respuesta.getId());
        assertEquals(2, respuesta.getProductos().size());
        assertEquals(18990, respuesta.getProductos().get(0).getSubtotal());
        assertEquals(15000, respuesta.getProductos().get(1).getSubtotal());
        assertTrue(respuesta.isEventoPublicado());
    }

    @Test
    void registrarPersisteElPedidoConElTotalCalculado() {
        alPersistirAsignarId(1L);

        pedidoService.registrar(solicitudValida());

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository).save(captor.capture());

        Pedido guardado = captor.getValue();
        assertEquals(33990, guardado.getTotal());
        assertEquals(2, guardado.getItems().size());
        assertEquals("Torta de chocolate", guardado.getItems().get(0).getNombre());
        assertEquals(1, guardado.getItems().get(0).getCantidad());
        assertEquals(18990, guardado.getItems().get(0).getSubtotal());
        assertNotNull(guardado.getFecha());
        assertSame(guardado, guardado.getItems().get(0).getPedido());
    }

    @Test
    void registrarPublicaElJsonDelPedidoCreado() {
        alPersistirAsignarId(42L);

        pedidoService.registrar(solicitudValida());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("pedidos"), captor.capture());

        String json = captor.getValue();
        assertTrue(json.contains("\"evento\":\"PedidoCreado\""));
        assertTrue(json.contains("\"id\":42"));
        assertTrue(json.contains("Torta de chocolate, Cupcakes vainilla"));
        assertTrue(json.contains("\"cantidad\":7"));
    }

    @Test
    void registrarRespondeEventoNoPublicadoSiKafkaFallaPeroElPedidoQuedaGuardado() {
        alPersistirAsignarId(11L);
        CompletableFuture<SendResult<String, String>> falla = new CompletableFuture<>();
        falla.completeExceptionally(new RuntimeException("broker caído"));
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(falla);

        PedidoResponse respuesta = pedidoService.registrar(solicitudValida());

        assertFalse(respuesta.isEventoPublicado());
        assertEquals(33990, respuesta.getTotal());
        verify(pedidoRepository).save(any(Pedido.class));
    }

    @Test
    void registrarRechazaClienteEnBlanco() {
        PedidoRequest solicitud = solicitudValida();
        solicitud.setCliente("   ");

        assertThrows(IllegalArgumentException.class, () -> pedidoService.registrar(solicitud));
        verificarQueNoSeGuarda();
    }

    @Test
    void registrarRechazaEmailEnBlanco() {
        PedidoRequest solicitud = solicitudValida();
        solicitud.setEmail(null);

        assertThrows(IllegalArgumentException.class, () -> pedidoService.registrar(solicitud));
        verificarQueNoSeGuarda();
    }

    @Test
    void registrarRechazaPedidoSinProductos() {
        PedidoRequest solicitud = solicitudValida();
        solicitud.setProductos(List.of());

        assertThrows(IllegalArgumentException.class, () -> pedidoService.registrar(solicitud));
        verificarQueNoSeGuarda();
    }

    @Test
    void registrarRechazaCantidadMenorAUno() {
        PedidoRequest solicitud = solicitudValida();
        solicitud.getProductos().get(0).setCantidad(0);

        assertThrows(IllegalArgumentException.class, () -> pedidoService.registrar(solicitud));
        verificarQueNoSeGuarda();
    }

    @Test
    void registrarRechazaPrecioUnitarioNegativo() {
        PedidoRequest solicitud = solicitudValida();
        solicitud.getProductos().get(1).setPrecioUnitario(-100);

        assertThrows(IllegalArgumentException.class, () -> pedidoService.registrar(solicitud));
        verificarQueNoSeGuarda();
    }

    @Test
    void obtenerDevuelveElPedidoCuandoExiste() {
        Pedido pedido = new Pedido();
        pedido.setId(3L);
        pedido.setCliente("Daniela Soto");
        pedido.setEmail("daniela@ejemplo.cl");
        pedido.setTotal(18990);
        when(pedidoRepository.findById(3L)).thenReturn(Optional.of(pedido));

        Optional<PedidoResponse> respuesta = pedidoService.obtener(3L);

        assertTrue(respuesta.isPresent());
        assertEquals(3L, respuesta.get().getId());
        assertEquals("Daniela Soto", respuesta.get().getCliente());
    }

    @Test
    void obtenerDevuelveVacioCuandoNoExiste() {
        when(pedidoRepository.findById(99L)).thenReturn(Optional.empty());

        assertTrue(pedidoService.obtener(99L).isEmpty());
    }

    // El repositorio devuelve el pedido con su id generado
    private void alPersistirAsignarId(Long id) {
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> {
            Pedido pedido = invocation.getArgument(0);
            pedido.setId(id);
            return pedido;
        });
    }

    private void verificarQueNoSeGuarda() {
        verify(pedidoRepository, never()).save(any(Pedido.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString());
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
