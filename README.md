# 🍰 Pedidos Service - Pastelería My Dreams

Microservicio de pedidos del sistema **Pastelería My Dreams**: recibe los pedidos desde el formulario del frontend, los persiste en MySQL (RF-07) y publica el evento `PedidoCreado` en el topic `pedidos` de Kafka (RF-08 / RNF-08).

## 📌 Versiones del proyecto

| Rama | Versión | Contenido |
| :--- | :--- | :--- |
| `version-1` | **Entrega 1** | Sin pedidos (solo catálogo). |
| `version-2` | **Entrega 2** | Flujo de pedidos con Kafka: registro persistido + evento `PedidoCreado`. |
| `version-3` | **Unidad 3** | Pendiente. |

`main` siempre lleva el último avance del desarrollo.

## 🏢 Equipo de Desarrollo
Diseñado y construido por **8 Digital**.

## 🛠️ Stack Tecnológico
* **Lenguaje:** Java 21
* **Framework:** Spring Boot 3.2.5 + Spring Web + Spring Data JPA (Hibernate)
* **Mensajería:** Apache Kafka (productor, topic `pedidos`)
* **Base de Datos:** MySQL local / Amazon AWS RDS
* **Gestor de dependencias:** Maven
* **Estructura de datos:** JSON

## 🚀 Endpoints Disponibles

| Método HTTP | Ruta | Descripción |
| :--- | :--- | :--- |
| `POST` | `/api/pedidos` | Registra un pedido nuevo: lo persiste y publica el evento `PedidoCreado` en Kafka. |
| `GET` | `/api/pedidos` | Lista todos los pedidos, los más recientes primero. |
| `GET` | `/api/pedidos/{id}` | Consulta un pedido por su id (devuelve `404` si no existe). |
| `PATCH` | `/api/pedidos/{id}/estado` | Cambia el estado de un pedido (RF-11): `200` actualizado, `400` si el estado no es válido, `404` si no existe. |

CORS está abierto solo para el frontend local `http://localhost:5173`.

### POST /api/pedidos

Cuerpo de la solicitud:

```json
{
  "cliente": "E2E Test",
  "email": "e2e@test.cl",
  "productos": [
    { "nombre": "Torta E2E", "cantidad": 2, "precioUnitario": 15000 }
  ]
}
```

Respuesta `201 Created`:

```json
{
  "id": 1,
  "cliente": "E2E Test",
  "email": "e2e@test.cl",
  "fecha": "2026-10-01T21:29:01.733033",
  "total": 30000,
  "productos": [
    { "nombre": "Torta E2E", "cantidad": 2, "precioUnitario": 15000, "subtotal": 30000 }
  ],
  "eventoPublicado": true
}
```

* El `total` se calcula en el servidor como la suma de `cantidad * precioUnitario` de cada producto: nunca se confía en un total enviado por el cliente.
* `eventoPublicado` es `true` cuando el broker de Kafka confirmó la publicación dentro del request (espera máxima de 5 s).

Validación de RF-07 → `400 Bad Request` con cuerpo `{"mensaje": "..."}`:

| Condición inválida | `mensaje` devuelto |
| :--- | :--- |
| Solicitud vacía | `La solicitud de pedido está vacía` |
| `cliente` ausente o en blanco | `El cliente es obligatorio` |
| `email` ausente o en blanco | `El email es obligatorio` |
| Sin productos | `El pedido debe incluir al menos un producto` |
| Producto sin nombre | `Cada producto debe tener un nombre` |
| `cantidad` menor a 1 | `La cantidad de cada producto debe ser al menos 1` |
| `precioUnitario` negativo | `El precio unitario no puede ser negativo` |

### GET /api/pedidos

Devuelve un arreglo con todos los pedidos (mismo shape que la respuesta del `POST`), ordenados del más reciente al más antiguo. En las lecturas `eventoPublicado` siempre es `false`, porque solo el `POST` publica el evento.

### GET /api/pedidos/{id}

Devuelve `200` con el pedido, o `404` si el id no existe.

### PATCH /api/pedidos/{id}/estado

Cambia el estado de un pedido (RF-11). Cuerpo: `{"estado": "EN_PREPARACION"}`. Estados válidos: `RECIBIDO` (estado con el que nace todo pedido), `EN_PREPARACION`, `DESPACHADO`, `ENTREGADO`. Devuelve `200` con el pedido actualizado, `400` con `{"mensaje": ...}` si el estado no es válido, o `404` si el id no existe. La columna `estado` la agrega automáticamente `ddl-auto=update` al arrancar.

## 📨 Kafka (RF-08 / RNF-08)

### Contrato del evento

Topic: `pedidos` (configurable con `app.kafka.topic`). El payload es un JSON serializado con el `ObjectMapper` de Spring. Ejemplo real publicado en la verificación E2E:

```json
{
  "evento": "PedidoCreado",
  "id": 1,
  "cliente": "E2E Test",
  "email": "e2e@test.cl",
  "producto": "Torta E2E",
  "cantidad": 2,
  "total": 30000,
  "fecha": "2026-10-01T21:29:01.7330338"
}
```

Campos del record `PedidoCreadoEvent`: `evento`, `id`, `cliente`, `email`, `producto`, `cantidad`, `total`, `fecha`. Nota: `producto` es la lista de nombres de los items unida por `", "` y `cantidad` es la suma de las cantidades de todos los items.

### Persistir antes de publicar (RNF-08)

`PedidoService.registrar()` ejecuta en este orden y en el mismo hilo:

1. `pedidoRepository.save(pedido)` → INSERT en las tablas `pedidos` y `pedido_items`.
2. `kafkaTemplate.send(...)` → publicación del evento `PedidoCreado`.

Verificado en la corrida E2E local: INSERT a las 21:29:01.733 → publicación a las 21:29:02.137, mismo thread. **Si Kafka falla, el pedido no se pierde**: ya quedó persistido, la publicación reintenta de forma acotada (ver productor más abajo) y la respuesta se devuelve con `eventoPublicado: false`.

### Productor (`KafkaProducerConfig`)

| Setting | Valor |
| :--- | :--- |
| `acks` | `all` |
| `retries` | `10` |
| `enable.idempotence` | `true` |
| `delivery.timeout.ms` | `30000` |
| Espera de confirmación en el request | 5 s |

## 🐳 Kafka local (broker)

Broker Kafka local en modo KRaft (sin ZooKeeper) con la imagen `apache/kafka:3.9.0`, vía Docker Compose incluido en este repositorio:

```powershell
docker compose up -d    # levanta el broker en el puerto 9092 y crea el topic `pedidos`
docker compose down     # baja el broker (conserva el volumen de datos)
docker compose down -v  # baja el broker y BORRA los datos (volumen)
```

* Contenedor: `pasteleria-kafka`.
* Incluye `healthcheck` (lista los topics cada 10 s) y volumen persistente `kafka-data`.
* El topic se crea automáticamente (`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"`).

Es solo para desarrollo local. El despliegue en la nube usará el mismo `docker-compose.yml` en EC2.

## ⚙️ Variables de entorno

La conexión a la base de datos se configura por variables de entorno, con defaults locales:

| Variable | Default local |
| :--- | :--- |
| `DB_URL` | `jdbc:mysql://localhost:3306/pasteleria_my_dreams?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `DB_USER` | `root` |
| `DB_PASS` | *(vacío)* |

Otras propiedades relevantes (`src/main/resources/application.properties`):

| Propiedad | Valor |
| :--- | :--- |
| `server.port` | `8082` |
| `spring.kafka.bootstrap-servers` | `localhost:9092` |
| `app.kafka.topic` | `pedidos` |
| `spring.jpa.hibernate.ddl-auto` | `update` (crea/actualiza `pedidos` y `pedido_items` al arrancar) |

## ▶️ Cómo correrlo local

Requisitos: JDK 21, MySQL 8 local con la base `pasteleria_my_dreams` y el broker Kafka arriba.

```powershell
docker compose up -d         # broker Kafka (en este repo)
.\mvnw.cmd spring-boot:run   # servicio en el puerto 8082
```

## 🧪 Tests

```powershell
.\mvnw.cmd test
```

Los tests corren sobre H2 en memoria (ver `src/test/resources/application.properties`), por lo que no requieren MySQL ni Kafka levantados: hay tests unitarios del servicio, tests MockMvc de los endpoints (incluido el cambio de estado RF-11) y un test de integración de publicación con broker embebido (`@EmbeddedKafka`).

## 🔄 Flujo end-to-end

```
Frontend (formulario)
   │  POST /api/pedidos
   ▼
pedidos-service (8082)
   │  1) INSERT en MySQL (pedidos + pedido_items)   ← siempre primero (RNF-08)
   │  2) publicar evento PedidoCreado
   ▼
Kafka topic `pedidos`
   ├──▶ notificaciones-service (8083)  → persiste la notificación (estado PENDIENTE)
   └──▶ estadisticas-service  (8081)   → actualiza pedidosTotales / montoTotalPedidos
```
