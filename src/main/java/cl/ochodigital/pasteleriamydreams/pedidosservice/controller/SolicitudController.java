package cl.ochodigital.pasteleriamydreams.pedidosservice.controller;

import cl.ochodigital.pasteleriamydreams.pedidosservice.dto.SolicitudRequest;
import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Solicitud;
import cl.ochodigital.pasteleriamydreams.pedidosservice.service.SolicitudService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Endpoints REST de las solicitudes de pedido de la Versión 1 (S2). Una solicitud
// NO es una venta: el usuario V1 pide sus delicias con este formulario y la
// pastelería le responde por correo. Requiere sesión con Google (JWT) con
// versión ≥ 1; el AdminApiKeyFilter sigue protegiendo solo el panel de /api/pedidos.
@RestController
@RequestMapping("/api/solicitudes")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://pasteleria-my-dreams-web-8digital.s3-website-us-east-1.amazonaws.com"
})
public class SolicitudController {

    private final SolicitudService solicitudService;

    public SolicitudController(SolicitudService solicitudService) {
        this.solicitudService = solicitudService;
    }

    // Registrar una solicitud nueva (la usa el formulario de la Versión 1)
    @PostMapping
    public ResponseEntity<?> crearSolicitud(@RequestBody SolicitudRequest solicitud) {
        try {
            Solicitud guardada = solicitudService.registrar(solicitud);
            return ResponseEntity.status(HttpStatus.CREATED).body(guardada);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage()));
        }
    }
}
