package cl.ochodigital.pasteleriamydreams.pedidosservice.service;

import cl.ochodigital.pasteleriamydreams.pedidosservice.dto.PedidoRequest;
import cl.ochodigital.pasteleriamydreams.pedidosservice.dto.PedidoResponse;
import cl.ochodigital.pasteleriamydreams.pedidosservice.event.PedidoCreadoEvent;
import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Pedido;
import cl.ochodigital.pasteleriamydreams.pedidosservice.model.PedidoItem;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.PedidoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// Registra, consulta y lista pedidos, y publica el evento PedidoCreado (RF-07 / RF-08)
@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    // Espera máxima de la confirmación de Kafka dentro del request
    private static final int TIMEOUT_PUBLICACION_SEGUNDOS = 5;

    private final PedidoRepository pedidoRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topicoPedidos;

    // Inyectamos el repositorio (H2 en tests, MySQL/RDS en runtime) y el productor de Kafka
    public PedidoService(PedidoRepository pedidoRepository,
                         KafkaTemplate<String, String> kafkaTemplate,
                         ObjectMapper objectMapper,
                         @Value("${app.kafka.topic}") String topicoPedidos) {
        this.pedidoRepository = pedidoRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topicoPedidos = topicoPedidos;
    }

    // Valida la solicitud, calcula el total, persiste el pedido y recién entonces publica el evento
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

        // 1) Primero se persiste: el pedido nunca se pierde aunque Kafka falle
        Pedido guardado = pedidoRepository.save(pedido);
        // 2) Después se publica el evento (RNF-08)
        boolean eventoPublicado = publicarEventoCreado(guardado);

        return armarRespuesta(guardado, eventoPublicado);
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

    // Publica PedidoCreado y devuelve true solo si el broker confirmó a tiempo
    private boolean publicarEventoCreado(Pedido pedido) {
        PedidoCreadoEvent evento = new PedidoCreadoEvent(
                "PedidoCreado",
                pedido.getId(),
                pedido.getCliente(),
                pedido.getEmail(),
                pedido.getItems().stream().map(PedidoItem::getNombre).collect(Collectors.joining(", ")),
                pedido.getItems().stream().mapToInt(PedidoItem::getCantidad).sum(),
                pedido.getTotal(),
                pedido.getFecha()
        );

        try {
            String json = objectMapper.writeValueAsString(evento);
            kafkaTemplate.send(topicoPedidos, json).get(TIMEOUT_PUBLICACION_SEGUNDOS, TimeUnit.SECONDS);
            log.info("Evento PedidoCreado publicado en el topic '{}' para el pedido {}", topicoPedidos, pedido.getId());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Publicación del evento interrumpida para el pedido {}", pedido.getId(), e);
            return false;
        } catch (Exception e) {
            // El pedido ya quedó guardado: solo registramos el fallo y respondemos eventoPublicado=false
            log.error("No se pudo publicar el evento del pedido {}: {}", pedido.getId(), e.getMessage());
            return false;
        }
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
