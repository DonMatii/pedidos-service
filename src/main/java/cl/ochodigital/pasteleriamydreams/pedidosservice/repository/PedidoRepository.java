package cl.ochodigital.pasteleriamydreams.pedidosservice.repository;

import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Acceso a datos de los pedidos
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    // Listado para la UI: los pedidos m�s recientes primero
    List<Pedido> findAllByOrderByIdDesc();

    // Consulta publica por el codigo opaco de seguimiento (RF-11)
    Optional<Pedido> findByCodigoConsulta(String codigoConsulta);

    // Carga el pedido con sus items para el desglose de productos (usado al cambiar estado)
    @Query("SELECT p FROM Pedido p JOIN FETCH p.items WHERE p.id = :id")
    Optional<Pedido> findByIdWithItems(@Param("id") Long id);

    // Uso unico del codigo de bienvenida: ese email ya lo canjeo?
    boolean existsByEmailIgnoreCaseAndCodigoDescuento(String email, String codigoDescuento);

    // Agrega por producto las unidades y el monto vendidos en un rango de pedidos (S3):
    // alimenta la seccion "que se vendio" del dashboard de administracion.
    // Se compara la FECHA DE CALENDARIO (CAST a date) y no el datetime: el driver JDBC con
    // serverTimezone=UTC convertía el parámetro LocalDateTime y corría el límite del rango
    // unas horas, dejando fuera los pedidos de la madrugada. Comparar date-to-date (y no
    // datetime) evita esa conversión y el día es el mismo que ve el admin. Válido en MySQL y H2.
    @Query("""
            SELECT i.nombre AS nombre,
                   SUM(i.cantidad) AS unidades,
                   SUM(i.cantidad * i.precioUnitario) AS monto
            FROM PedidoItem i
            WHERE CAST(i.pedido.fecha AS date) >= :desde
              AND CAST(i.pedido.fecha AS date) <= :hasta
            GROUP BY i.nombre
            ORDER BY SUM(i.cantidad) DESC
            """)
    List<VentasPorProducto> buscarVentasPorProducto(@Param("desde") LocalDate desde,
                                                     @Param("hasta") LocalDate hasta);

    // Proyeccion ligera de una fila agregada por producto: nombre, unidades y monto
    interface VentasPorProducto {
        String getNombre();
        Long getUnidades();
        Long getMonto();
    }
}
