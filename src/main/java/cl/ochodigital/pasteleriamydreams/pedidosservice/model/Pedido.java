package cl.ochodigital.pasteleriamydreams.pedidosservice.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Pedido de compra persistido por JPA (RF-07)
@Entity
@Table(name = "pedidos")
@Data
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String cliente;

    @Column(nullable = false)
    private String email;

    // Fecha de creación del pedido (se completa con la hora actual al registrarlo)
    private LocalDateTime fecha = LocalDateTime.now();

    // Total en pesos chilenos (suma de cantidad × precio unitario)
    @Column(nullable = false)
    private Integer total;

    // Detalle del pedido: se crea y borra junto con el pedido padre
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<PedidoItem> items = new ArrayList<>();
}
