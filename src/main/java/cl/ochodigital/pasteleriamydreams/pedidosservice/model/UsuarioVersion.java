package cl.ochodigital.pasteleriamydreams.pedidosservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

// Versión habilitada por usuario (demo de las tres versiones). La asignación
// es UN DATO: se cambia con un UPDATE sobre esta tabla, nunca con código.
// 1 = solo V1 (OAuth + solicitud), 2 = V1 + V2 (carrito, compra, seguimiento),
// 3 = V1 + V2 + V3 (actualizaciones de estado por streaming).
@Entity
@Table(name = "usuarios_version")
@Data
public class UsuarioVersion {

    @Id
    private String email;

    private Integer version;
}
