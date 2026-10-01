package cl.ochodigital.pasteleriamydreams.pedidosservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Microservicio de pedidos - recibe pedidos, persiste y publica eventos en Kafka (RF-07, RF-08)
@SpringBootApplication
public class PedidosServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PedidosServiceApplication.class, args);
    }

}
