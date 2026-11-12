package cl.ochodigital.pasteleriamydreams.pedidosservice.repository;

import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Solicitud;
import org.springframework.data.jpa.repository.JpaRepository;

// Acceso a las solicitudes de pedido de la Versión 1 (tabla `solicitudes`)
public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {
}
