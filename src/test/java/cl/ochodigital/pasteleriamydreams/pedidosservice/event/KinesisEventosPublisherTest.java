package cl.ochodigital.pasteleriamydreams.pedidosservice.event;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;
import software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException;
import software.amazon.awssdk.services.kinesis.model.StreamStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// El stream de eventos de negocio (Entrega 3) recibe cada evento con:
//  - nombre del stream configurable (app.kinesis.stream)
//  - partition key = id del pedido (los eventos de un mismo pedido conservan
//    su orden dentro del shard)
//  - el mismo JSON de contrato que viaja por Kafka
// Y la publicación es best-effort como la de Kafka: el pedido ya está guardado,
// un fallo del stream se registra y jamás rompe la venta.
class KinesisEventosPublisherTest {

    private final KinesisClient kinesisClient = mock(KinesisClient.class);

    private KinesisEventosPublisher publisher(boolean autoCrear) {
        return new KinesisEventosPublisher(kinesisClient, "eventos-negocio", true, autoCrear);
    }

    @Test
    void publicarEnviaElJsonConPartitionKeyDelPedido() {
        when(kinesisClient.putRecord(any(PutRecordRequest.class)))
                .thenReturn(PutRecordResponse.builder().sequenceNumber("1").build());

        publisher(true).publicar("pedido_creado", 501L, "{\"evento\":\"PedidoCreado\",\"id\":501}");

        ArgumentCaptor<PutRecordRequest> captor = ArgumentCaptor.forClass(PutRecordRequest.class);
        verify(kinesisClient).putRecord(captor.capture());
        assertEquals("eventos-negocio", captor.getValue().streamName());
        assertEquals("501", captor.getValue().partitionKey());
        assertEquals("{\"evento\":\"PedidoCreado\",\"id\":501}",
                captor.getValue().data().asUtf8String());
    }

    @Test
    void unFalloDelStreamNuncaRompeLaVenta() {
        when(kinesisClient.putRecord(any(PutRecordRequest.class)))
                .thenThrow(new RuntimeException("stream caído"));

        assertDoesNotThrow(() -> publisher(true).publicar("pedido_creado", 502L, "{}"));
    }

    @Test
    void siElStreamNoExisteYAutoCrearEstaActivoLoCreaYEsperaAActive() {
        // Primera lectura: no existe; tras crear, el stream está ACTIVE
        when(kinesisClient.describeStream(any(DescribeStreamRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("no existe").build())
                .thenReturn(DescribeStreamResponse.builder()
                        .streamDescription(d -> d.streamName("eventos-negocio").streamStatus(StreamStatus.ACTIVE))
                        .build());
        when(kinesisClient.putRecord(any(PutRecordRequest.class)))
                .thenReturn(PutRecordResponse.builder().sequenceNumber("1").build());

        publisher(true).publicar("pedido_creado", 503L, "{}");

        verify(kinesisClient).createStream(any(software.amazon.awssdk.services.kinesis.model.CreateStreamRequest.class));
        verify(kinesisClient).putRecord(any(PutRecordRequest.class));
    }

    @Test
    void siAutoCrearEstaDesactivadoNoIntentaCrearElStream() {
        when(kinesisClient.describeStream(any(DescribeStreamRequest.class)))
                .thenReturn(DescribeStreamResponse.builder()
                        .streamDescription(d -> d.streamName("eventos-negocio").streamStatus(StreamStatus.ACTIVE))
                        .build());
        when(kinesisClient.putRecord(any(PutRecordRequest.class)))
                .thenReturn(PutRecordResponse.builder().sequenceNumber("1").build());

        publisher(false).publicar("pedido_creado", 504L, "{}");

        verify(kinesisClient, never()).createStream(any(software.amazon.awssdk.services.kinesis.model.CreateStreamRequest.class));
    }
}
