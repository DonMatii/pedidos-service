package cl.ochodigital.pasteleriamydreams.pedidosservice.repository;

import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    // Agrega por producto las unidades y el monto vendidos en un rango de pedidos (S3):
    // alimenta la seccion "que se vendio" del dashboard de administracion
    @Query("""
            SELECT i.nombre AS nombre,
                   SUM(i.cantidad) AS unidades,
                   SUM(i.cantidad * i.precioUnitario) AS monto
            FROM PedidoItem i
            WHERE i.pedido.fecha >= :desde AND i.pedido.fecha < :hasta
            GROUP BY i.nombre
            ORDER BY SUM(i.cantidad) DESC
            """)
    List<VentasPorProducto> buscarVentasPorProducto(@Param("desde") LocalDateTime desde,
                                                     @Param("hasta") LocalDateTime hasta);

    // Proyeccion ligera de una fila agregada por producto: nombre, unidades y monto
    interface VentasPorProducto {
        String getNombre();
        Long getUnidades();
        Long getMonto();
    }
}
