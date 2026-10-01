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
        LocalDateTime fecha) {
}
