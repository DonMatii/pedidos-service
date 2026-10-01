package cl.ochodigital.pasteleriamydreams.pedidosservice.service;

import cl.ochodigital.pasteleriamydreams.pedidosservice.dto.PedidoRequest;
import cl.ochodigital.pasteleriamydreams.pedidosservice.dto.PedidoResponse;
import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Pedido;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Pruebas unitarias del servicio de pedidos (repositorio simulado, sin H2 ni Kafka)
@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @InjectMocks
    private PedidoService pedidoService;

    @Test
    void registrarCalculaElTotalSumandoTodosLosItems() {
        // 1 torta a 18.990 + 6 cupcakes a 2.500 = 33.990
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> {
            Pedido pedido = invocation.getArgument(0);
            pedido.setId(7L);
            return pedido;
        });

        PedidoResponse respuesta = pedidoService.registrar(solicitudValida());

        assertEquals(33990, respuesta.getTotal());
        assertEquals(7L, respuesta.getId());
        assertEquals(2, respuesta.getProductos().size());
        assertEquals(18990, respuesta.getProductos().get(0).getSubtotal());
        assertEquals(15000, respuesta.getProductos().get(1).getSubtotal());
        assertFalse(respuesta.isEventoPublicado());
    }

    @Test
    void registrarPersisteElPedidoConElTotalCalculado() {
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> {
            Pedido pedido = invocation.getArgument(0);
            pedido.setId(1L);
            return pedido;
        });

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
    void registrarRechazaClienteEnBlanco() {
        PedidoRequest solicitud = solicitudValida();
        solicitud.setCliente("   ");

        assertThrows(IllegalArgumentException.class, () -> pedidoService.registrar(solicitud));
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void registrarRechazaEmailEnBlanco() {
        PedidoRequest solicitud = solicitudValida();
        solicitud.setEmail(null);

        assertThrows(IllegalArgumentException.class, () -> pedidoService.registrar(solicitud));
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void registrarRechazaPedidoSinProductos() {
        PedidoRequest solicitud = solicitudValida();
        solicitud.setProductos(List.of());

        assertThrows(IllegalArgumentException.class, () -> pedidoService.registrar(solicitud));
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void registrarRechazaCantidadMenorAUno() {
        PedidoRequest solicitud = solicitudValida();
        solicitud.getProductos().get(0).setCantidad(0);

        assertThrows(IllegalArgumentException.class, () -> pedidoService.registrar(solicitud));
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void registrarRechazaPrecioUnitarioNegativo() {
        PedidoRequest solicitud = solicitudValida();
        solicitud.getProductos().get(1).setPrecioUnitario(-100);

        assertThrows(IllegalArgumentException.class, () -> pedidoService.registrar(solicitud));
        verify(pedidoRepository, never()).save(any(Pedido.class));
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
        solicitud.setProductos(new java.util.ArrayList<>(List.of(torta, cupcakes)));
        return solicitud;
    }
}
