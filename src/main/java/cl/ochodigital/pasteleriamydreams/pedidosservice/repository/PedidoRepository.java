package cl.ochodigital.pasteleriamydreams.pedidosservice.repository;

import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Acceso a datos de los pedidos
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    // Listado para la UI: los pedidos m�s recientes primero
    List<Pedido> findAllByOrderByIdDesc();

    // Consulta publica por el codigo opaco de seguimiento (RF-11)
    Optional<Pedido> findByCodigoConsulta(String codigoConsulta);

    // Uso unico del codigo de bienvenida: ese email ya lo canjeo?
    boolean existsByEmailIgnoreCaseAndCodigoDescuento(String email, String codigoDescuento);
}
