package cl.ochodigital.pasteleriamydreams.pedidosservice.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

// Respuesta estándar de los endpoints de pedidos (RF-07 / RF-08)
@Data
public class PedidoResponse {

    private Long id;
    private String cliente;
    private String email;
    private LocalDateTime fecha;
    private Integer total;
    // Estado actual del pedido que devuelven los endpoints (RF-11)
    private String estado;

    // Codigo opaco de seguimiento para la consulta publica (RF-11)
    private String codigoConsulta;
    private List<ProductoResponse> productos;
    // true cuando el evento PedidoCreado fue confirmado por Kafka (T5)
    private boolean eventoPublicado;

    // Eco de los items del pedido con su subtotal calculado
    @Data
    public static class ProductoResponse {
        private String nombre;
        private Integer cantidad;
        private Integer precioUnitario;
        private Integer subtotal;
    }
}
