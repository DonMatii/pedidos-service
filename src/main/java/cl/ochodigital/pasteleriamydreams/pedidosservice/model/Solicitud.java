package cl.ochodigital.pasteleriamydreams.pedidosservice.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

// Solicitud de pedido persistida por JPA (Versión 1, S2). NO es una venta: no
// tiene carrito, total ni código de seguimiento; es el formulario con el que el
// usuario V1 pide sus delicias y la pastelería le responde por correo.
@Entity
@Table(name = "solicitudes")
@Data
public class Solicitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String email;

    // Contacto telefónico (opcional: el contrato solo exige nombre, email y detalle)
    private String telefono;

    // Productos y cantidades elegidas, en texto legible
    @Column(nullable = false, columnDefinition = "TEXT")
    private String detalle;

    // Mensaje opcional del cliente (ej: "Para el sábado por la tarde")
    @Column(columnDefinition = "TEXT")
    private String mensaje;

    // Fecha de creación de la solicitud (se completa con la hora actual al registrarla)
    private LocalDateTime fecha = LocalDateTime.now();
}
