package cl.ochodigital.pasteleriamydreams.pedidosservice.service;

import cl.ochodigital.pasteleriamydreams.pedidosservice.dto.PedidoRequest;
import cl.ochodigital.pasteleriamydreams.pedidosservice.dto.PedidoResponse;
import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Pedido;
import cl.ochodigital.pasteleriamydreams.pedidosservice.model.PedidoItem;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.PedidoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

// Registra, consulta y lista pedidos (RF-07)
@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;

    // Inyectamos el repositorio que conecta con la base de datos (H2 en tests, MySQL/RDS en runtime)
    public PedidoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    // Valida la solicitud, calcula el total y persiste el pedido
    public PedidoResponse registrar(PedidoRequest solicitud) {
        validar(solicitud);

        Pedido pedido = new Pedido();
        pedido.setCliente(solicitud.getCliente().trim());
        pedido.setEmail(solicitud.getEmail().trim());

        int total = 0;
        for (PedidoRequest.ProductoRequest producto : solicitud.getProductos()) {
            PedidoItem item = new PedidoItem();
            item.setNombre(producto.getNombre().trim());
            item.setCantidad(producto.getCantidad());
            item.setPrecioUnitario(producto.getPrecioUnitario());
            item.setPedido(pedido);
            pedido.getItems().add(item);
            total += producto.getCantidad() * producto.getPrecioUnitario();
        }
        pedido.setTotal(total);

        Pedido guardado = pedidoRepository.save(pedido);
        return armarRespuesta(guardado, false);
    }

    // Busca un pedido por su id (devuelve vacío si no existe)
    public Optional<PedidoResponse> obtener(Long id) {
        return pedidoRepository.findById(id).map(pedido -> armarRespuesta(pedido, false));
    }

    // Lista todos los pedidos del más reciente al más antiguo
    public List<PedidoResponse> listar() {
        return pedidoRepository.findAllByOrderByIdDesc().stream()
                .map(pedido -> armarRespuesta(pedido, false))
                .toList();
    }

    // Reglas de negocio de RF-07: si algo falta o no calza, se responde 400
    private void validar(PedidoRequest solicitud) {
        if (solicitud == null) {
            throw new IllegalArgumentException("La solicitud de pedido está vacía");
        }
        if (solicitud.getCliente() == null || solicitud.getCliente().isBlank()) {
            throw new IllegalArgumentException("El cliente es obligatorio");
        }
        if (solicitud.getEmail() == null || solicitud.getEmail().isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }
        if (solicitud.getProductos() == null || solicitud.getProductos().isEmpty()) {
            throw new IllegalArgumentException("El pedido debe incluir al menos un producto");
        }
        for (PedidoRequest.ProductoRequest producto : solicitud.getProductos()) {
            if (producto == null || producto.getNombre() == null || producto.getNombre().isBlank()) {
                throw new IllegalArgumentException("Cada producto debe tener un nombre");
            }
            if (producto.getCantidad() == null || producto.getCantidad() < 1) {
                throw new IllegalArgumentException("La cantidad de cada producto debe ser al menos 1");
            }
            if (producto.getPrecioUnitario() == null || producto.getPrecioUnitario() < 0) {
                throw new IllegalArgumentException("El precio unitario no puede ser negativo");
            }
        }
    }

    // Convierte la entidad en la respuesta que se devuelve al cliente
    private PedidoResponse armarRespuesta(Pedido pedido, boolean eventoPublicado) {
        PedidoResponse respuesta = new PedidoResponse();
        respuesta.setId(pedido.getId());
        respuesta.setCliente(pedido.getCliente());
        respuesta.setEmail(pedido.getEmail());
        respuesta.setFecha(pedido.getFecha());
        respuesta.setTotal(pedido.getTotal());
        respuesta.setEventoPublicado(eventoPublicado);
        respuesta.setProductos(pedido.getItems().stream().map(this::armarProducto).toList());
        return respuesta;
    }

    private PedidoResponse.ProductoResponse armarProducto(PedidoItem item) {
        PedidoResponse.ProductoResponse producto = new PedidoResponse.ProductoResponse();
        producto.setNombre(item.getNombre());
        producto.setCantidad(item.getCantidad());
        producto.setPrecioUnitario(item.getPrecioUnitario());
        producto.setSubtotal(item.getSubtotal());
        return producto;
    }
}
