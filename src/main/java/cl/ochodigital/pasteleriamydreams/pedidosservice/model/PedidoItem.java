package cl.ochodigital.pasteleriamydreams.pedidosservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

// Línea de producto dentro de un pedido (RF-07)
@Entity
@Table(name = "pedido_items")
@Data
public class PedidoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private Integer cantidad;

    // Precio en pesos chilenos, igual que el catálogo
    private Integer precioUnitario;

    // Referencia al pedido dueño (no se serializa para evitar ciclos en el JSON)
    @ManyToOne
    @JoinColumn(name = "pedido_id")
    @JsonIgnore
    private Pedido pedido;

    // Subtotal calculado (cantidad × precio unitario) que viaja en el JSON
    @Transient
    public Integer getSubtotal() {
        if (cantidad == null || precioUnitario == null) {
            return 0;
        }
        return cantidad * precioUnitario;
    }
}
