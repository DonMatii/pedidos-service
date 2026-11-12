package cl.ochodigital.pasteleriamydreams.pedidosservice.service;

import cl.ochodigital.pasteleriamydreams.pedidosservice.dto.SolicitudRequest;
import cl.ochodigital.pasteleriamydreams.pedidosservice.event.SolicitudCreadoEvent;
import cl.ochodigital.pasteleriamydreams.pedidosservice.model.Solicitud;
import cl.ochodigital.pasteleriamydreams.pedidosservice.repository.SolicitudRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

// Registra las solicitudes de pedido de la Versión 1 (S2) y publica el evento
// SolicitudCreado. Una solicitud NO es una venta: no hay carrito, total ni
// código de seguimiento; la pastelería responde por correo.
@Service
public class SolicitudService {

    private static final Logger log = LoggerFactory.getLogger(SolicitudService.class);

    // Espera máxima de la confirmación de Kafka dentro del request
    private static final int TIMEOUT_PUBLICACION_SEGUNDOS = 5;

    private final SolicitudRepository solicitudRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    // Topic propio de las solicitudes: mismo patrón que `pedidos-estados`
    private final String topicoSolicitudes;

    // Inyectamos el repositorio (H2 en tests, MySQL/RDS en runtime) y el productor
    // de Kafka, la misma infraestructura de publicación que usa PedidoService
    public SolicitudService(SolicitudRepository solicitudRepository,
                            KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper,
                            @Value("${app.kafka.topic.solicitudes:solicitudes}") String topicoSolicitudes) {
        this.solicitudRepository = solicitudRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topicoSolicitudes = topicoSolicitudes;
    }

    // Valida la solicitud, la persiste y recién entonces publica el evento
    public Solicitud registrar(SolicitudRequest solicitud) {
        validar(solicitud);

        Solicitud nueva = new Solicitud();
        nueva.setNombre(solicitud.getNombre().trim());
        nueva.setEmail(solicitud.getEmail().trim());
        nueva.setTelefono(solicitud.getTelefono() == null || solicitud.getTelefono().isBlank()
                ? null
                : solicitud.getTelefono().trim());
        nueva.setDetalle(solicitud.getDetalle().trim());
        nueva.setMensaje(solicitud.getMensaje() == null || solicitud.getMensaje().isBlank()
                ? null
                : solicitud.getMensaje().trim());

        // 1) Primero se persiste: la solicitud nunca se pierde aunque Kafka falle
        Solicitud guardada = solicitudRepository.save(nueva);
        // 2) Después se publica el evento (misma política best-effort que PedidoService)
        publicarEventoCreado(guardada);

        return guardada;
    }

    // Publica SolicitudCreado y devuelve true solo si el broker confirmó a tiempo.
    // Misma forma que PedidoService#publicarEventoCreado: JSON por ObjectMapper,
    // envío con confirmación por KafkaTemplate y fallo sin romper el request
    // (la solicitud ya quedó guardada). Sin Kinesis: el stream de eventos es de
    // analítica de pedidos y una solicitud no es una venta.
    private boolean publicarEventoCreado(Solicitud solicitud) {
        SolicitudCreadoEvent evento = new SolicitudCreadoEvent(
                "SolicitudCreado",
                solicitud.getId(),
                solicitud.getNombre(),
                solicitud.getEmail(),
                solicitud.getTelefono(),
                solicitud.getDetalle(),
                solicitud.getMensaje(),
                solicitud.getFecha()
        );

        try {
            String json = objectMapper.writeValueAsString(evento);
            kafkaTemplate.send(topicoSolicitudes, json).get(TIMEOUT_PUBLICACION_SEGUNDOS, TimeUnit.SECONDS);
            log.info("Evento SolicitudCreado publicado en el topic '{}' para la solicitud {}",
                    topicoSolicitudes, solicitud.getId());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Publicación del evento interrumpida para la solicitud {}", solicitud.getId(), e);
            return false;
        } catch (Exception e) {
            // La solicitud ya quedó guardada: solo registramos el fallo
            log.error("No se pudo publicar el evento de la solicitud {}: {}", solicitud.getId(), e.getMessage());
            return false;
        }
    }

    // Reglas del contrato: nombre, email y detalle son obligatorios; si algo falta
    // o está en blanco, se responde 400
    private void validar(SolicitudRequest solicitud) {
        if (solicitud == null) {
            throw new IllegalArgumentException("La solicitud de pedido está vacía");
        }
        if (solicitud.getNombre() == null || solicitud.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (solicitud.getEmail() == null || solicitud.getEmail().isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }
        if (solicitud.getDetalle() == null || solicitud.getDetalle().isBlank()) {
            throw new IllegalArgumentException("El detalle de la solicitud es obligatorio");
        }
    }
}
