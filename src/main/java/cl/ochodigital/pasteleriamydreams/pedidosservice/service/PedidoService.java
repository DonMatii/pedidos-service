package cl.ochodigital.pasteleriamydreams.pedidosservice.service;

import cl.ochodigital.pasteleriamydreams.pedidosservice.client.DeliciaClient;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// Registra, consulta y lista pedidos, y publica el evento PedidoCreado (RF-07 / RF-08)
@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    // Espera máxima de la confirmación de Kafka dentro del request
    private static final int TIMEOUT_PUBLICACION_SEGUNDOS = 5;

    // Estados válidos de un pedido (RF-11): todo lo demás se rechaza con 400
    private static final Set<String> ESTADOS_VALIDOS =
            Set.of("RECIBIDO", "EN_PREPARACION", "DESPACHADO", "ENTREGADO");

    // Codigo de bienvenida: -10% en el primer pedido de quien se conecta la primera vez
    public static final String CODIGO_BIENVENIDO = "BIENVENIDO10";
    private static final int PORCENTAJE_BIENVENIDO = 10;

    // Delicia del Día: -10% sobre el subtotal completo si el pedido la incluye (S2)
    private static final int PORCENTAJE_DELICIA = 10;

    private final PedidoRepository pedidoRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final DeliciaClient deliciaClient;
    private final String topicoPedidos;

    // Inyectamos el repositorio (H2 en tests, MySQL/RDS en runtime), el productor de
    // Kafka y el cliente de catalogo-service para consultar la Delicia del Día
    public PedidoService(PedidoRepository pedidoRepository,
                         KafkaTemplate<String, String> kafkaTemplate,
                         ObjectMapper objectMapper,
                         DeliciaClient deliciaClient,
                         @Value("${app.kafka.topic}") String topicoPedidos) {
        this.pedidoRepository = pedidoRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.deliciaClient = deliciaClient;
        this.topicoPedidos = topicoPedidos;
    }

    // Valida la solicitud, calcula el total, persiste el pedido y recién entonces publica el evento
    public PedidoResponse registrar(PedidoRequest solicitud) {
        validar(solicitud);

        Pedido pedido = new Pedido();
        pedido.setCliente(solicitud.getCliente().trim());
        pedido.setEmail(solicitud.getEmail().trim());
        // Codigo opaco de seguimiento: la consulta publica usa este codigo, nunca el id
        pedido.setCodigoConsulta(UUID.randomUUID().toString().replace("-", ""));

        int subtotal = 0;
        for (PedidoRequest.ProductoRequest producto : solicitud.getProductos()) {
            PedidoItem item = new PedidoItem();
            item.setNombre(producto.getNombre().trim());
            item.setCantidad(producto.getCantidad());
            item.setPrecioUnitario(producto.getPrecioUnitario());
            item.setPedido(pedido);
            pedido.getItems().add(item);
            subtotal += producto.getCantidad() * producto.getPrecioUnitario();
        }

        // Descuento de bienvenida: solo aplica si llego el codigo valido (validado arriba)
        String codigo = (solicitud.getCodigoDescuento() == null || solicitud.getCodigoDescuento().isBlank())
                ? null
                : CODIGO_BIENVENIDO;
        int descuentoBienvenida = (codigo != null) ? subtotal * PORCENTAJE_BIENVENIDO / 100 : 0;
        // Delicia del Día: -10% sobre el subtotal, una sola vez por transacción (S2).
        // El total pasa a ser subtotal - bienvenida - delicia; la columna `descuento`
        // persiste la suma de ambos (sin cambios de esquema)
        int descuentoDelicia = descuentoDelicia(solicitud.getProductos(), subtotal);
        pedido.setCodigoDescuento(codigo);
        pedido.setDescuento(descuentoBienvenida + descuentoDelicia);
        pedido.setTotal(subtotal - descuentoBienvenida - descuentoDelicia);

        // 1) Primero se persiste: el pedido nunca se pierde aunque Kafka falle
        Pedido guardado = pedidoRepository.save(pedido);
        // 2) Después se publica el evento (RNF-08)
        boolean eventoPublicado = publicarEventoCreado(guardado);

        return armarRespuesta(guardado, eventoPublicado);
    }

    // Busca un pedido por su id (uso interno/administrativo, protegido con API key)
    public Optional<PedidoResponse> obtener(Long id) {
        return pedidoRepository.findById(id).map(pedido -> armarRespuesta(pedido, false));
    }

    // Busca un pedido por su codigo opaco de seguimiento (consulta publica RF-11)
    public Optional<PedidoResponse> obtenerPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return pedidoRepository.findByCodigoConsulta(codigo.trim())
                .map(pedido -> armarRespuesta(pedido, false));
    }

    // Lista todos los pedidos del más reciente al más antiguo
    public List<PedidoResponse> listar() {
        return pedidoRepository.findAllByOrderByIdDesc().stream()
                .map(pedido -> armarRespuesta(pedido, false))
                .toList();
    }

    // Cambia el estado de un pedido, lo persiste y devuelve la respuesta actualizada (RF-11)
    public Optional<PedidoResponse> cambiarEstado(Long id, String estado) {
        Optional<Pedido> pedido = pedidoRepository.findById(id);
        if (pedido.isEmpty()) {
            return Optional.empty();
        }
        if (estado == null || !ESTADOS_VALIDOS.contains(estado)) {
            throw new IllegalArgumentException("Estado invalido: " + estado);
        }
        Pedido actualizado = pedido.get();
        actualizado.setEstado(estado);
        Pedido guardado = pedidoRepository.save(actualizado);
        return Optional.of(armarRespuesta(guardado, false));
    }

    // Delicia del Día: -10% sobre el subtotal completo, una sola vez por transacción,
    // solo si algún item del pedido coincide con la delicia de hoy (match por nombre,
    // trim() + equalsIgnoreCase). Fail-open: si catalogo-service no responde, el pedido
    // se crea igual y simplemente no recibe este descuento.
    private int descuentoDelicia(List<PedidoRequest.ProductoRequest> productos, int subtotal) {
        String delicia;
        try {
            delicia = deliciaClient.obtenerNombreDelicia().orElse(null);
        } catch (Exception e) {
            log.warn("Fallo inesperado al consultar la delicia del día: el pedido sigue sin ese descuento ({})",
                    e.getMessage());
            return 0;
        }
        if (delicia == null) {
            return 0;
        }
        String buscada = delicia.trim();
        boolean incluyeLaDelicia = productos.stream().anyMatch(producto ->
                producto.getNombre() != null && buscada.equalsIgnoreCase(producto.getNombre().trim()));
        if (!incluyeLaDelicia) {
            return 0;
        }
        int descuento = subtotal * PORCENTAJE_DELICIA / 100;
        log.info("El pedido incluye la Delicia del Día '{}': -10% sobre el subtotal ({})", delicia, descuento);
        return descuento;
    }

    // Publica PedidoCreado y devuelve true solo si el broker confirmó a tiempo
    private boolean publicarEventoCreado(Pedido pedido) {
        PedidoCreadoEvent evento = new PedidoCreadoEvent(
                "PedidoCreado",
                pedido.getId(),
                pedido.getCliente(),
                pedido.getEmail(),
                pedido.getItems().stream().map(i -> i.getNombre() + " x" + i.getCantidad()).collect(Collectors.joining(", ")),
                pedido.getItems().stream().mapToInt(PedidoItem::getCantidad).sum(),
                pedido.getTotal(),
                pedido.getFecha(),
                pedido.getCodigoConsulta()
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
        String codigoDescuento = solicitud.getCodigoDescuento();
        if (codigoDescuento != null && !codigoDescuento.isBlank()
                && !CODIGO_BIENVENIDO.equalsIgnoreCase(codigoDescuento.trim())) {
            throw new IllegalArgumentException("Código de descuento inválido: " + codigoDescuento.trim());
        }
        // Uso unico por email: si ese email ya canjeo el codigo, el backend lo rechaza
        if (codigoDescuento != null && !codigoDescuento.isBlank()
                && CODIGO_BIENVENIDO.equalsIgnoreCase(codigoDescuento.trim())
                && pedidoRepository.existsByEmailIgnoreCaseAndCodigoDescuento(
                        solicitud.getEmail().trim(), CODIGO_BIENVENIDO)) {
            throw new IllegalArgumentException(
                    "El código de descuento ya fue canjeado para este email");
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
        int descuento = pedido.getDescuento() == null ? 0 : pedido.getDescuento();
        respuesta.setTotal(pedido.getTotal());
        respuesta.setSubtotal(pedido.getTotal() + descuento);
        respuesta.setDescuento(descuento);
        respuesta.setCodigoDescuento(pedido.getCodigoDescuento());
        respuesta.setEstado(pedido.getEstado());
        respuesta.setCodigoConsulta(pedido.getCodigoConsulta());
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
