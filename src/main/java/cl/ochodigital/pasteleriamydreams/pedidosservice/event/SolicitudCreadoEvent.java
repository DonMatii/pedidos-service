package cl.ochodigital.pasteleriamydreams.pedidosservice.event;

import java.time.LocalDateTime;

// Evento que se publica en el topic `solicitudes` cuando nace una solicitud de
// pedido de la Versión 1 (S2). Misma forma que PedidoCreadoEvent: un record
// serializado a JSON que notificaciones-service consume para enviar el correo.
public record SolicitudCreadoEvent(
        String evento,
        Long id,
        String nombre,
        String email,
        String telefono,
        String detalle,
        String mensaje,
        LocalDateTime fecha) {
}
