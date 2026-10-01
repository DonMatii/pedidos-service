package cl.ochodigital.pasteleriamydreams.pedidosservice.repository;

import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Acceso a datos de los pedidos
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    // Listado para la UI: los pedidos más recientes primero
    List<Pedido> findAllByOrderByIdDesc();
}
