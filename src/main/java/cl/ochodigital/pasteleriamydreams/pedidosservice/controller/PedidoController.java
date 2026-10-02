package cl.ochodigital.pasteleriamydreams.pedidosservice.controller;

import cl.ochodigital.pasteleriamydreams.pedidosservice.dto.PedidoRequest;
import cl.ochodigital.pasteleriamydreams.pedidosservice.dto.PedidoResponse;
import cl.ochodigital.pasteleriamydreams.pedidosservice.service.PedidoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Endpoints REST de pedidos (RF-07)
@RestController
@RequestMapping("/api/pedidos")
@CrossOrigin(origins = {"http://localhost:5173"})
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    // Registrar un pedido nuevo (lo usa el carrito del frontend)
    @PostMapping
    public ResponseEntity<?> crearPedido(@RequestBody PedidoRequest solicitud) {
        try {
            PedidoResponse respuesta = pedidoService.registrar(solicitud);
            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage()));
        }
    }

    // Consultar un pedido por su id (lo usa el frontend y la verificación)
    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponse> obtenerPedido(@PathVariable Long id) {
        return pedidoService.obtener(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Listar los pedidos, los más recientes primero
    @GetMapping
    public List<PedidoResponse> listarPedidos() {
        return pedidoService.listar();
    }

    // Cambiar el estado de un pedido (RF-11)
    @PatchMapping("/{id}/estado")
    public ResponseEntity<?> cambiarEstadoPedido(@PathVariable Long id, @RequestBody Map<String, String> cuerpo) {
        try {
            return pedidoService.cambiarEstado(id, cuerpo.get("estado"))
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage()));
        }
    }
}
