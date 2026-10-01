# 🍰 Pedidos Service - Pastelería My Dreams

Microservicio de pedidos del sistema **Pastelería My Dreams**: recibe los pedidos, los persiste en la base de datos y publica eventos (RF-07, RF-08).

## 🛠️ Stack Tecnológico
* **Lenguaje:** Java 21
* **Framework:** Spring Boot 3.2.5 + Spring Web + Spring Data JPA
* **Mensajería:** Apache Kafka (topic `pedidos`)
* **Base de Datos:** MySQL / Amazon AWS RDS
* **Gestor de dependencias:** Maven

## 🚀 Cómo correrlo local

```powershell
.\mvnw spring-boot:run
```

El servicio escucha en el puerto **8082**.

## ⚙️ Variables de entorno

La conexión a la base de datos se configura por variables de entorno, con defaults locales:

| Variable | Default local |
| :--- | :--- |
| `DB_URL` | `jdbc:mysql://localhost:3306/pasteleria_my_dreams?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `DB_USER` | `root` |
| `DB_PASS` | *(vacío)* |

## 📨 Kafka

Este microservicio **consumirá y publicará en el topic `pedidos`**. La configuración del productor/consumidor está pendiente en la tarea siguiente.

## 🧪 Tests

```powershell
.\mvnw test
```

Los tests corren sobre H2 en memoria (ver `src/test/resources/application.properties`), por lo que no requieren MySQL ni Kafka levantados.
