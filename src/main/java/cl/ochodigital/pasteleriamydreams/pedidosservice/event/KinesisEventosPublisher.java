package cl.ochodigital.pasteleriamydreams.pedidosservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException;

// Publica los eventos de negocio en el stream de Kinesis (Entrega 3: el
// "streaming de datos"). El contrato es el mismo JSON que viaja por Kafka:
// Kafka mueve TAREAS (colas de correos) y Kinesis mueve DATOS (analítica).
//
// Best-effort, igual que la publicación en Kafka: el pedido ya está persistido,
// un fallo del stream se registra en el log y jamás rompe la venta. La
// partition key es el id del pedido para que los eventos de un mismo pedido
// conserven su orden dentro del shard.
@Component
public class KinesisEventosPublisher {

    private static final Logger log = LoggerFactory.getLogger(KinesisEventosPublisher.class);

    private final KinesisClient kinesisClient;
    private final String stream;
    private final boolean habilitado;
    private final boolean autoCrearStream;

    public KinesisEventosPublisher(KinesisClient kinesisClient,
                                   @Value("${app.kinesis.stream:eventos-negocio}") String stream,
                                   @Value("${app.kinesis.habilitado:false}") boolean habilitado,
                                   @Value("${app.kinesis.auto-crear-stream:true}") boolean autoCrearStream) {
        this.kinesisClient = kinesisClient;
        this.stream = stream;
        this.habilitado = habilitado;
        this.autoCrearStream = autoCrearStream;
    }

    public void publicar(String tipoEvento, Long pedidoId, String json) {
        if (!habilitado) {
            return;
        }
        try {
            asegurarStream();
            kinesisClient.putRecord(PutRecordRequest.builder()
                    .streamName(stream)
                    .partitionKey(String.valueOf(pedidoId))
                    .data(SdkBytes.fromUtf8String(json))
                    .build());
            log.info("Evento {} del pedido {} publicado en el stream '{}'", tipoEvento, pedidoId, stream);
        } catch (Exception e) {
            log.error("No se pudo publicar el evento {} del pedido {} en el stream '{}': {}",
                    tipoEvento, pedidoId, stream, e.getMessage());
        }
    }

    // En local el stream se crea solo (LocalStack); en el lab se pre-crea o se
    // deja esta misma garantía si el rol lo permite. Ojo: crear el stream es
    // ASÍNCRONO — queda en CREATING y hay que esperar a ACTIVE antes de escribir
    private void asegurarStream() {
        try {
            kinesisClient.describeStream(DescribeStreamRequest.builder().streamName(stream).build());
        } catch (ResourceNotFoundException e) {
            if (!autoCrearStream) {
                throw e;
            }
            log.warn("El stream '{}' no existe: creándolo (modo local)", stream);
            kinesisClient.createStream(CreateStreamRequest.builder()
                    .streamName(stream)
                    .shardCount(1)
                    .build());
            esperarStreamActivo();
        }
    }

    // Espera acotada (hasta ~15 s) a que el stream deje de estar en CREATING
    private void esperarStreamActivo() {
        for (int intento = 0; intento < 30; intento++) {
            try {
                var descripcion = kinesisClient.describeStream(
                        DescribeStreamRequest.builder().streamName(stream).build());
                String estado = descripcion.streamDescription().streamStatusAsString();
                if ("ACTIVE".equals(estado)) {
                    return;
                }
                log.info("El stream '{}' está en estado {}... esperando", stream, estado);
            } catch (Exception e) {
                log.debug("Aún no se puede leer el estado del stream '{}': {}", stream, e.getMessage());
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("El stream '{}' no informó ACTIVE en 15 s: se intenta publicar igual", stream);
    }
}
