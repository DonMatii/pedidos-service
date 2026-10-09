package cl.ochodigital.pasteleriamydreams.pedidosservice.event;

import java.time.LocalDateTime;

// Evento que se publica en el topic `pedidos` cuando nace un pedido (RF-08)
public record PedidoCreadoEvent(
        String evento,
        Long id,
        String cliente,
        String email,
        String producto,
        Integer cantidad,
        Integer total,
        LocalDateTime fecha,
        // Codigo opaco de seguimiento (RF-11): opcional y al final para que los
        // consumidores que no lo usan (estadisticas-service) sigan leyendo igual
        String codigoConsulta) {
}
