package cl.ochodigital.pasteleriamydreams.pedidosservice.event;

import java.time.LocalDateTime;
import java.util.List;

// Evento publicado en el topic `pedidos-estados` cuando la administradora cambia
// el estado de un pedido (RF-11). Topic PROPIO y separado de `pedidos` a proposito:
// el listener de notificaciones guarda una fila unica por pedidoId, asi que si este
// evento viajara en el mismo topico se descartaria como duplicado.
//
// Lleva estadoAnterior y estadoNuevo para que el correo al cliente nombre el
// estado al que PASO, y codigoConsulta para el enlace de seguimiento publico.
// Lleva productos para que el correo al cliente muestre lo que compro.
public record PedidoEstadoCambiadoEvent(
        String evento,
        Long id,
        String cliente,
        String email,
        String estadoAnterior,
        String estadoNuevo,
        Integer total,
        LocalDateTime fecha,
        String codigoConsulta,
        List<ProductoEstado> productos) {

    // Desglose de un producto dentro del pedido, para mostrar en el correo al cliente
    public record ProductoEstado(
            String nombre,
            Integer cantidad,
            Integer precioUnitario,
            Integer subtotal) {
    }
}
