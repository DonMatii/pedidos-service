package cl.ochodigital.pasteleriamydreams.pedidosservice.dto;

import lombok.Data;

import java.util.List;

// Cuerpo que recibe POST /api/pedidos para registrar un pedido (RF-07)
@Data
public class PedidoRequest {

    private String cliente;
    private String email;
    private List<ProductoRequest> productos;
    // Codigo de descuento opcional (BIENVENIDO10 = -10% de bienvenida)
    private String codigoDescuento;

    // Producto pedido: nombre del catálogo, cantidad y precio unitario
    @Data
    public static class ProductoRequest {
        private String nombre;
        private Integer cantidad;
        private Integer precioUnitario;
    }
}
