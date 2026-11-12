package cl.ochodigital.pasteleriamydreams.pedidosservice.dto;

import lombok.Data;

// Cuerpo que recibe POST /api/solicitudes para registrar una solicitud de
// pedido de la Versión 1 (S2). Obligatorios: nombre, email y detalle;
// telefono y mensaje son opcionales.
@Data
public class SolicitudRequest {

    private String nombre;
    private String email;
    private String telefono;
    // Productos y cantidades elegidas, en texto legible
    private String detalle;
    private String mensaje;
}
