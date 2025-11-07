# RabbitMQ com Quarkus e Gradle - Guia Completo

## Índice
- [Introdução](#introdução)
- [Setup e Configuração](#setup-e-configuração)
- [Conceitos Fundamentais](#conceitos-fundamentais)
- [Producers (Enviando Mensagens)](#producers-enviando-mensagens)
- [Consumers (Recebendo Mensagens)](#consumers-recebendo-mensagens)
- [Padrões de Mensageria](#padrões-de-mensageria)
- [Serialização e Deserialização](#serialização-e-deserialização)
- [Tratamento de Erros e Dead Letter Queue](#tratamento-de-erros-e-dead-letter-queue)
- [Configurações Avançadas](#configurações-avançadas)
- [Testes](#testes)
- [Comparação com Spring AMQP](#comparação-com-spring-amqp)
- [Troubleshooting](#troubleshooting)
- [Melhores Práticas](#melhores-práticas)

---

## Introdução

O Quarkus utiliza **SmallRye Reactive Messaging** para integração com RabbitMQ. A abordagem é baseada em **anotações** e suporta tanto programação **bloqueante** quanto **reativa** (Mutiny).

### Por que RabbitMQ?

- ✅ **Desacoplamento**: Producers e consumers não precisam estar online ao mesmo tempo
- ✅ **Escalabilidade**: Múltiplos consumers processam mensagens em paralelo
- ✅ **Confiabilidade**: Mensagens persistidas não são perdidas
- ✅ **Roteamento flexível**: Exchanges e bindings permitem roteamento complexo
- ✅ **Dead Letter Queues**: Tratamento automático de mensagens com falha

---

## Setup e Configuração

### 1. Dependências do Gradle

Adicione ao seu `build.gradle`:

```gradle
dependencies {
    // Plataforma Quarkus
    implementation enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}")

    // RabbitMQ com SmallRye Reactive Messaging
    implementation 'io.quarkus:quarkus-messaging-rabbitmq'

    // JSON serialization (recomendado)
    implementation 'io.quarkus:quarkus-rest-jackson'

    // Validação (opcional mas recomendado)
    implementation 'io.quarkus:quarkus-hibernate-validator'

    // Health checks para RabbitMQ
    implementation 'io.quarkus:quarkus-smallrye-health'

    // Testes
    testImplementation 'io.quarkus:quarkus-junit5'
    testImplementation 'io.rest-assured:rest-assured'
}
```

### 2. Configuração do RabbitMQ

Crie ou edite `src/main/resources/application.yml`:

```yaml
# Configuração do RabbitMQ
rabbitmq-host: localhost
rabbitmq-port: 5672
rabbitmq-username: guest
rabbitmq-password: guest

# Configuração do SmallRye Reactive Messaging
mp:
  messaging:
    # Configuração do conector RabbitMQ
    connector:
      smallrye-rabbitmq:
        host: ${rabbitmq-host}
        port: ${rabbitmq-port}
        username: ${rabbitmq-username}
        password: ${rabbitmq-password}
        # Reconexão automática
        reconnect-attempts: 100
        reconnect-interval: 10

# Logging para debug
quarkus:
  log:
    category:
      "io.smallrye.reactive.messaging":
        level: DEBUG
```

### 3. Docker Compose para Desenvolvimento

Crie `docker-compose.yml` na raiz do projeto:

```yaml
version: '3.8'

services:
  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    container_name: rabbitmq-dev
    ports:
      - "5672:5672"   # AMQP
      - "15672:15672" # Management UI
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    healthcheck:
      test: rabbitmq-diagnostics -q ping
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  rabbitmq_data:
```

Inicie o RabbitMQ:
```bash
docker-compose up -d
```

Acesse a UI de gerenciamento: http://localhost:15672 (guest/guest)

---

## Conceitos Fundamentais

### Exchange Types

1. **Direct**: Roteamento exato por routing key
2. **Fanout**: Broadcast para todas as filas vinculadas
3. **Topic**: Roteamento com pattern matching (wildcards)
4. **Headers**: Roteamento baseado em headers

### Anatomia de uma Mensagem

```
┌─────────────────────────────────┐
│         Message                 │
├─────────────────────────────────┤
│ Headers (metadata)              │
│  - content-type                 │
│  - message-id                   │
│  - timestamp                    │
│  - custom headers               │
├─────────────────────────────────┤
│ Payload (body)                  │
│  - JSON, String, bytes, etc.    │
└─────────────────────────────────┘
```

### Fluxo Básico

```
Producer → Exchange → Routing → Queue → Consumer
           (type)     (key)     (name)
```

---

## Producers (Enviando Mensagens)

### 1. Producer Simples (Bloqueante)

```java
package br.com.iagoomes.messaging.producer;

import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import java.time.Instant;

@ApplicationScoped
public class OrderProducer {

    @Inject
    @Channel("orders-out")  // Nome do canal definido no application.yml
    Emitter<OrderCreatedEvent> emitter;

    /**
     * Envia mensagem de forma bloqueante
     */
    public void sendOrderCreated(OrderCreatedEvent event) {
        emitter.send(event);
    }

    /**
     * Envia mensagem com metadata customizada
     */
    public void sendOrderCreatedWithMetadata(OrderCreatedEvent event) {

        // Cria metadata RabbitMQ
        OutgoingRabbitMQMetadata metadata = new OutgoingRabbitMQMetadata.Builder()
            .withRoutingKey("order.created." + event.getOrderType())
            .withHeader("x-source", "order-service")
            .withHeader("x-timestamp", Instant.now().toString())
            .withPriority(5)
            .withDeliveryMode(2)  // 2 = persistent
            .build();

        // Envia mensagem com metadata
        emitter.send(Message.of(event).addMetadata(metadata));
    }

    /**
     * Envia múltiplas mensagens
     */
    public void sendBatch(List<OrderCreatedEvent> events) {
        events.forEach(this::sendOrderCreated);
    }
}
```

### 2. Producer Reativo (Não-Bloqueante)

```java
package br.com.iagoomes.messaging.producer;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class ReactiveOrderProducer {

    @Inject
    @Channel("orders-out")
    Emitter<OrderCreatedEvent> emitter;

    /**
     * Envia mensagem de forma reativa
     * Retorna Uni para encadear operações
     */
    public Uni<Void> sendOrderCreatedAsync(OrderCreatedEvent event) {
        return Uni.createFrom().completionStage(
            emitter.send(event)
        );
    }

    /**
     * Envia mensagem com tratamento de erro
     */
    public Uni<Void> sendWithErrorHandling(OrderCreatedEvent event) {
        return Uni.createFrom().completionStage(emitter.send(event))
            .onFailure().invoke(err ->
                System.err.println("Failed to send message: " + err.getMessage())
            )
            .onFailure().retry().atMost(3);
    }
}
```

### 3. Configuração do Canal Producer

Em `application.yml`:

```yaml
mp:
  messaging:
    outgoing:
      orders-out:
        connector: smallrye-rabbitmq
        exchange:
          name: orders
          type: topic
          durable: true
          auto-delete: false
        routing-key: "order.created"
        default-routing-key: "order.created"
        # Confirmação de entrega
        confirmation-timeout: 5000
```

---

## Consumers (Recebendo Mensagens)

### 1. Consumer Simples (Bloqueante)

```java
package br.com.iagoomes.messaging.consumer;

import io.smallrye.reactive.messaging.rabbitmq.IncomingRabbitMQMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OrderConsumer {

    private static final Logger LOG = Logger.getLogger(OrderConsumer.class);

    /**
     * Consumidor bloqueante simples
     * Apenas recebe o payload
     */
    @Incoming("orders-in")
    public void consumeOrder(OrderCreatedEvent event) {
        LOG.infof("Received order: %s", event.getOrderId());

        // Processa a mensagem
        processOrder(event);

        // ✅ ACK automático se não houver exceção
    }

    /**
     * Consumidor com acesso ao Message completo
     * Permite ACK/NACK manual
     */
    @Incoming("orders-in")
    public void consumeOrderWithMetadata(Message<OrderCreatedEvent> message) {

        OrderCreatedEvent event = message.getPayload();

        // Acessa metadata RabbitMQ
        message.getMetadata(IncomingRabbitMQMetadata.class).ifPresent(metadata -> {
            LOG.infof("Routing key: %s", metadata.getRoutingKey());
            LOG.infof("Exchange: %s", metadata.getExchange());
            LOG.infof("Headers: %s", metadata.getHeaders());
        });

        try {
            processOrder(event);
            message.ack();  // ✅ ACK manual
        } catch (Exception e) {
            LOG.error("Failed to process order", e);
            message.nack(e);  // ❌ NACK - mensagem vai para DLQ
        }
    }

    private void processOrder(OrderCreatedEvent event) {
        // Lógica de negócio aqui
    }
}
```

### 2. Consumer Reativo (Não-Bloqueante)

```java
package br.com.iagoomes.messaging.consumer;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ReactiveOrderConsumer {

    private static final Logger LOG = Logger.getLogger(ReactiveOrderConsumer.class);

    @Inject
    OrderService orderService;

    /**
     * Consumidor reativo que retorna Uni<Void>
     */
    @Incoming("orders-in")
    public Uni<Void> consumeOrderAsync(OrderCreatedEvent event) {

        LOG.infof("Processing order: %s", event.getOrderId());

        return orderService.processOrderAsync(event)
            .onFailure().invoke(err ->
                LOG.errorf("Failed to process order %s: %s",
                    event.getOrderId(), err.getMessage())
            )
            .onFailure().retry().atMost(3)
            .replaceWithVoid();
    }

    /**
     * Consumidor reativo com ACK manual
     */
    @Incoming("orders-in")
    public Uni<Void> consumeOrderWithManualAck(Message<OrderCreatedEvent> message) {

        OrderCreatedEvent event = message.getPayload();

        return orderService.processOrderAsync(event)
            .onItem().transformToUni(result -> {
                LOG.infof("Successfully processed order: %s", event.getOrderId());
                return Uni.createFrom().completionStage(message.ack());
            })
            .onFailure().recoverWithUni(err -> {
                LOG.errorf("Failed to process order %s", event.getOrderId());
                return Uni.createFrom().completionStage(message.nack(err));
            });
    }
}
```

### 3. Configuração do Canal Consumer

Em `application.yml`:

```yaml
mp:
  messaging:
    incoming:
      orders-in:
        connector: smallrye-rabbitmq
        queue:
          name: orders-queue
          durable: true
          auto-delete: false
        exchange:
          name: orders
          type: topic
          durable: true
        routing-keys: "order.#"  # Wildcard: order.created, order.updated, etc.
        # Configurações de performance
        auto-acknowledgment: false  # ACK manual
        failure-strategy: reject    # reject, requeue, ignore
        max-incoming-internal-buffer-size: 100
```

---

## Padrões de Mensageria

### 1. Work Queue (Competição)

**Uso:** Distribuir tarefas entre múltiplos workers

```yaml
# application.yml
mp:
  messaging:
    outgoing:
      tasks-out:
        connector: smallrye-rabbitmq
        exchange:
          name: ""  # Default exchange
        routing-key: "tasks-queue"

    incoming:
      tasks-in:
        connector: smallrye-rabbitmq
        queue:
          name: tasks-queue
          durable: true
        # Prefetch: quantas mensagens cada consumer pega por vez
        prefetch: 1  # Fair dispatch
```

```java
// Producer
@ApplicationScoped
public class TaskProducer {
    @Inject
    @Channel("tasks-out")
    Emitter<Task> emitter;

    public void submitTask(Task task) {
        emitter.send(task);
    }
}

// Consumer (múltiplas instâncias competem)
@ApplicationScoped
public class TaskWorker {
    @Incoming("tasks-in")
    public void processTask(Task task) {
        // Apenas 1 worker processa cada mensagem
        performHeavyWork(task);
    }
}
```

---

### 2. Pub/Sub (Fanout)

**Uso:** Broadcast para múltiplos subscribers

```yaml
# application.yml
mp:
  messaging:
    outgoing:
      notifications-out:
        connector: smallrye-rabbitmq
        exchange:
          name: notifications
          type: fanout
          durable: true

    incoming:
      # Email subscriber
      notifications-email:
        connector: smallrye-rabbitmq
        queue:
          name: notifications-email-queue
          durable: false
          auto-delete: true
        exchange:
          name: notifications
          type: fanout

      # SMS subscriber
      notifications-sms:
        connector: smallrye-rabbitmq
        queue:
          name: notifications-sms-queue
          durable: false
          auto-delete: true
        exchange:
          name: notifications
          type: fanout
```

```java
// Producer
@ApplicationScoped
public class NotificationProducer {
    @Inject
    @Channel("notifications-out")
    Emitter<Notification> emitter;

    public void sendNotification(Notification notification) {
        emitter.send(notification);  // Vai para TODOS os subscribers
    }
}

// Email Consumer
@ApplicationScoped
public class EmailNotificationConsumer {
    @Incoming("notifications-email")
    public void sendEmail(Notification notification) {
        emailService.send(notification);
    }
}

// SMS Consumer
@ApplicationScoped
public class SmsNotificationConsumer {
    @Incoming("notifications-sms")
    public void sendSms(Notification notification) {
        smsService.send(notification);
    }
}
```

---

### 3. Routing (Direct)

**Uso:** Roteamento baseado em routing key exata

```yaml
# application.yml
mp:
  messaging:
    outgoing:
      logs-out:
        connector: smallrye-rabbitmq
        exchange:
          name: logs
          type: direct
          durable: true

    incoming:
      # Consumer para logs de erro
      logs-error:
        connector: smallrye-rabbitmq
        queue:
          name: logs-error-queue
        exchange:
          name: logs
          type: direct
        routing-keys: "error"

      # Consumer para logs de info
      logs-info:
        connector: smallrye-rabbitmq
        queue:
          name: logs-info-queue
        exchange:
          name: logs
          type: direct
        routing-keys: "info"
```

```java
// Producer
@ApplicationScoped
public class LogProducer {
    @Inject
    @Channel("logs-out")
    Emitter<LogMessage> emitter;

    public void sendLog(LogMessage log) {
        OutgoingRabbitMQMetadata metadata = new OutgoingRabbitMQMetadata.Builder()
            .withRoutingKey(log.getLevel())  // "error", "info", "warning"
            .build();

        emitter.send(Message.of(log).addMetadata(metadata));
    }
}

// Error Consumer
@ApplicationScoped
public class ErrorLogConsumer {
    @Incoming("logs-error")
    public void handleError(LogMessage log) {
        // Apenas logs com routing key "error"
        alertOps(log);
    }
}
```

---

### 4. Topics (Pattern Matching)

**Uso:** Roteamento com wildcards

```yaml
# application.yml
mp:
  messaging:
    outgoing:
      events-out:
        connector: smallrye-rabbitmq
        exchange:
          name: events
          type: topic
          durable: true

    incoming:
      # Todos os eventos de pedidos
      order-events:
        connector: smallrye-rabbitmq
        queue:
          name: order-events-queue
        exchange:
          name: events
          type: topic
        routing-keys: "order.#"  # order.created, order.updated, etc.

      # Apenas eventos de criação
      creation-events:
        connector: smallrye-rabbitmq
        queue:
          name: creation-events-queue
        exchange:
          name: events
          type: topic
        routing-keys: "*.created"  # order.created, user.created, etc.
```

**Wildcards:**
- `*` - substitui exatamente 1 palavra
- `#` - substitui 0 ou mais palavras

```java
// Producer
@ApplicationScoped
public class EventProducer {
    @Inject
    @Channel("events-out")
    Emitter<DomainEvent> emitter;

    public void publishEvent(DomainEvent event) {
        String routingKey = event.getEntityType() + "." + event.getEventType();
        // Exemplos: "order.created", "user.updated", "payment.cancelled"

        OutgoingRabbitMQMetadata metadata = new OutgoingRabbitMQMetadata.Builder()
            .withRoutingKey(routingKey)
            .build();

        emitter.send(Message.of(event).addMetadata(metadata));
    }
}

// Consumer para todos eventos de pedidos
@ApplicationScoped
public class OrderEventConsumer {
    @Incoming("order-events")
    public void handleOrderEvent(DomainEvent event) {
        // Recebe: order.created, order.updated, order.cancelled, etc.
    }
}

// Consumer apenas para criações
@ApplicationScoped
public class CreationEventConsumer {
    @Incoming("creation-events")
    public void handleCreation(DomainEvent event) {
        // Recebe: order.created, user.created, payment.created, etc.
    }
}
```

---

## Serialização e Deserialização

### 1. Conversor JSON Customizado

```java
package br.com.iagoomes.messaging.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.MessageConverter;
import io.vertx.core.buffer.Buffer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import java.lang.reflect.Type;

@ApplicationScoped
public class JsonMessageConverter implements MessageConverter {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public boolean canConvert(Message<?> in, Type target) {
        // Verifica se pode converter
        return in.getPayload() instanceof Buffer;
    }

    @Override
    public Message<?> convert(Message<?> in, Type target) {
        try {
            Buffer buffer = (Buffer) in.getPayload();
            byte[] bytes = buffer.getBytes();
            Object converted = objectMapper.readValue(bytes, objectMapper.constructType(target));
            return in.withPayload(converted);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize message", e);
        }
    }
}
```

### 2. DTO com Validação

```java
package br.com.iagoomes.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class OrderCreatedEvent {

    @NotNull(message = "Order ID is required")
    @JsonProperty("order_id")
    private UUID orderId;

    @NotNull
    @JsonProperty("customer_id")
    private UUID customerId;

    @NotBlank
    @JsonProperty("order_type")
    private String orderType;

    @NotNull
    @Positive
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @NotNull
    @JsonProperty("created_at")
    private Instant createdAt;

    // Getters, setters, constructors

    public OrderCreatedEvent() {
        this.createdAt = Instant.now();
    }

    public OrderCreatedEvent(UUID orderId, UUID customerId, String orderType, BigDecimal totalAmount) {
        this();
        this.orderId = orderId;
        this.customerId = customerId;
        this.orderType = orderType;
        this.totalAmount = totalAmount;
    }

    // Getters e Setters
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

---

## Tratamento de Erros e Dead Letter Queue

### 1. Configuração de DLQ

```yaml
# application.yml
mp:
  messaging:
    incoming:
      orders-in:
        connector: smallrye-rabbitmq
        queue:
          name: orders-queue
          durable: true
          # Configuração de DLQ
          x-dead-letter-exchange: orders-dlx
          x-dead-letter-routing-key: orders.failed
          # TTL: mensagem expira após 1 hora
          x-message-ttl: 3600000
          # Tamanho máximo da fila
          x-max-length: 10000
        exchange:
          name: orders
          type: topic
        routing-keys: "order.#"
        # Estratégia de falha
        failure-strategy: reject  # Envia para DLQ
        auto-acknowledgment: false

      # Consumer da DLQ
      orders-dlq:
        connector: smallrye-rabbitmq
        queue:
          name: orders-dlq-queue
          durable: true
        exchange:
          name: orders-dlx
          type: topic
        routing-keys: "orders.failed"
```

### 2. Consumer com Retry Automático

```java
package br.com.iagoomes.messaging.consumer;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import java.time.Duration;

@ApplicationScoped
public class ResilientOrderConsumer {

    private static final Logger LOG = Logger.getLogger(ResilientOrderConsumer.class);

    @Incoming("orders-in")
    public Uni<Void> consumeOrder(Message<OrderCreatedEvent> message) {

        OrderCreatedEvent event = message.getPayload();

        return processOrder(event)
            // Retry com backoff exponencial
            .onFailure().retry()
                .withBackOff(Duration.ofSeconds(1), Duration.ofMinutes(1))
                .atMost(5)
            // Se todas as tentativas falharem
            .onFailure().recoverWithUni(err -> {
                LOG.errorf("Failed to process order %s after retries: %s",
                    event.getOrderId(), err.getMessage());

                // NACK - mensagem vai para DLQ
                return Uni.createFrom().completionStage(message.nack(err));
            })
            // Sucesso - ACK
            .onItem().transformToUni(result ->
                Uni.createFrom().completionStage(message.ack())
            );
    }

    private Uni<Void> processOrder(OrderCreatedEvent event) {
        // Sua lógica de negócio aqui
        return Uni.createFrom().voidItem();
    }
}
```

### 3. DLQ Consumer (Monitoring/Alerting)

```java
package br.com.iagoomes.messaging.consumer;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DeadLetterConsumer {

    private static final Logger LOG = Logger.getLogger(DeadLetterConsumer.class);

    @Incoming("orders-dlq")
    public void handleDeadLetter(Message<OrderCreatedEvent> message) {

        OrderCreatedEvent event = message.getPayload();

        // Log detalhado
        LOG.errorf("Message in DLQ - Order ID: %s", event.getOrderId());

        // Metadata do RabbitMQ
        message.getMetadata(IncomingRabbitMQMetadata.class).ifPresent(metadata -> {
            LOG.infof("Original exchange: %s", metadata.getHeader("x-first-death-exchange"));
            LOG.infof("Original routing key: %s", metadata.getHeader("x-first-death-routing-key"));
            LOG.infof("Death count: %s", metadata.getHeader("x-death"));
        });

        // Envia alerta para equipe de ops
        alertOps(event);

        // Persiste para análise posterior
        saveToDatabaseForAnalysis(event);

        // ACK para remover da DLQ
        message.ack();
    }

    private void alertOps(OrderCreatedEvent event) {
        // Slack, PagerDuty, etc.
    }

    private void saveToDatabaseForAnalysis(OrderCreatedEvent event) {
        // Salva em tabela de erros
    }
}
```

---

## Configurações Avançadas

### 1. Performance Tuning

```yaml
# application.yml
mp:
  messaging:
    connector:
      smallrye-rabbitmq:
        # Connection pooling
        channel-pool-max-size: 10
        channel-pool-max-wait-time: 10000

    incoming:
      orders-in:
        # Prefetch: quantas mensagens pegar por vez
        prefetch: 10  # Aumentar para maior throughput

        # Buffer interno
        max-incoming-internal-buffer-size: 500

        # Múltiplas instâncias do consumer
        # (aumenta paralelismo)
        broadcast: false

    outgoing:
      orders-out:
        # Confirmação de publicação
        confirmation-timeout: 5000

        # Max tentativas de envio
        max-inflight-messages: 1000
```

### 2. Health Check

```java
package br.com.iagoomes.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class RabbitMQHealthCheck implements HealthCheck {

    // Quarkus injeta automaticamente a conexão
    @Override
    public HealthCheckResponse call() {
        // SmallRye RabbitMQ faz check automático da conexão
        return HealthCheckResponse.up("rabbitmq");
    }
}
```

Acesse: http://localhost:8080/q/health

### 3. Métricas com Micrometer

```yaml
# application.yml
quarkus:
  micrometer:
    enabled: true
    export:
      prometheus:
        enabled: true
```

```java
package br.com.iagoomes.messaging.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class MeteredOrderConsumer {

    @Inject
    MeterRegistry registry;

    @Incoming("orders-in")
    public void consumeOrder(OrderCreatedEvent event) {
        Timer.Sample sample = Timer.start(registry);

        try {
            processOrder(event);

            // Métrica de sucesso
            registry.counter("orders.processed.success").increment();
        } catch (Exception e) {
            // Métrica de erro
            registry.counter("orders.processed.error").increment();
            throw e;
        } finally {
            // Tempo de processamento
            sample.stop(Timer.builder("orders.processing.time")
                .tag("order_type", event.getOrderType())
                .register(registry));
        }
    }

    private void processOrder(OrderCreatedEvent event) {
        // Lógica aqui
    }
}
```

Acesse métricas: http://localhost:8080/q/metrics

---

## Testes

### 1. Teste com Testcontainers

Adicione ao `build.gradle`:
```gradle
testImplementation 'io.quarkus:quarkus-junit5'
testImplementation 'io.rest-assured:rest-assured'
testImplementation 'org.testcontainers:testcontainers:1.19.0'
testImplementation 'org.testcontainers:rabbitmq:1.19.0'
```

```java
package br.com.iagoomes.messaging;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@QuarkusTest
@QuarkusTestResource(RabbitMQTestResource.class)
public class OrderMessagingTest {

    @Inject
    @Channel("orders-out")
    Emitter<OrderCreatedEvent> emitter;

    @Inject
    OrderTestConsumer testConsumer;

    @Test
    public void testOrderMessageFlow() {
        // Arrange
        OrderCreatedEvent event = new OrderCreatedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "ONLINE",
            new BigDecimal("1234.56")
        );

        // Act
        emitter.send(event);

        // Assert - aguarda mensagem ser consumida
        await()
            .atMost(5, TimeUnit.SECONDS)
            .until(() -> testConsumer.getReceivedEvents().size() == 1);

        OrderCreatedEvent received = testConsumer.getReceivedEvents().get(0);
        assertEquals(event.getOrderId(), received.getOrderId());
        assertEquals(event.getTotalAmount(), received.getTotalAmount());
    }
}
```

### 2. Test Resource

```java
package br.com.iagoomes.messaging;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.RabbitMQContainer;
import java.util.Map;

public class RabbitMQTestResource implements QuarkusTestResourceLifecycleManager {

    private RabbitMQContainer container;

    @Override
    public Map<String, String> start() {
        container = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
            .withExposedPorts(5672, 15672);

        container.start();

        return Map.of(
            "rabbitmq-host", container.getHost(),
            "rabbitmq-port", container.getMappedPort(5672).toString(),
            "rabbitmq-username", "guest",
            "rabbitmq-password", "guest"
        );
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
```

### 3. Test Consumer

```java
package br.com.iagoomes.messaging;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class OrderTestConsumer {

    private final List<OrderCreatedEvent> receivedEvents = new ArrayList<>();

    @Incoming("orders-in")
    public void consume(OrderCreatedEvent event) {
        receivedEvents.add(event);
    }

    public List<OrderCreatedEvent> getReceivedEvents() {
        return receivedEvents;
    }

    public void clear() {
        receivedEvents.clear();
    }
}
```

---

## Comparação com Spring AMQP

| Aspecto | Quarkus SmallRye | Spring AMQP |
|---------|------------------|-------------|
| **Configuração** | Anotações + YAML | Anotações + Java Config |
| **Paradigma** | Reactive Messaging | Imperativo / Reativo (opcional) |
| **Producer** | `@Channel` + `Emitter` | `RabbitTemplate` |
| **Consumer** | `@Incoming` | `@RabbitListener` |
| **Async** | Nativo (Mutiny) | `@Async` ou Reactor |
| **Backpressure** | ✅ Built-in | ⚠️ Com Reactor |
| **Configuração DLQ** | YAML | Java Config |
| **Health Check** | ✅ Automático | Manual |
| **Startup** | ✅ Rápido (0.5s) | ⚠️ Normal (3-5s) |
| **Memória** | ✅ Baixa (~70MB) | ⚠️ Normal (~150MB) |

### Exemplo Spring AMQP (para comparação)

```java
// Spring Producer
@Service
public class OrderProducer {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void send(OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend("orders", "order.created", event);
    }
}

// Spring Consumer
@Service
public class OrderConsumer {
    @RabbitListener(queues = "orders-queue")
    public void consume(OrderCreatedEvent event) {
        processOrder(event);
    }
}

// Config
@Configuration
public class RabbitConfig {
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange("orders");
    }

    @Bean
    public Queue queue() {
        return QueueBuilder.durable("orders-queue")
            .withArgument("x-dead-letter-exchange", "orders-dlx")
            .build();
    }

    @Bean
    public Binding binding() {
        return BindingBuilder.bind(queue())
            .to(exchange())
            .with("order.#");
    }
}
```

---

## Troubleshooting

### Problema 1: Mensagens não são consumidas

**Sintomas:**
- Produtor envia mensagens
- Consumer não processa
- Mensagens ficam na fila

**Diagnóstico:**
```bash
# Verifica filas no RabbitMQ
docker exec rabbitmq-dev rabbitmqctl list_queues name messages consumers
```

**Soluções:**
1. ✅ Verifica se routing key está correta
2. ✅ Verifica se exchange e queue estão vinculados (binding)
3. ✅ Verifica logs do consumer: `quarkus.log.category."io.smallrye.reactive.messaging".level=DEBUG`

---

### Problema 2: Connection refused

**Sintomas:**
```
io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused
```

**Soluções:**
1. ✅ Verifica se RabbitMQ está rodando: `docker ps`
2. ✅ Verifica host/port no `application.yml`
3. ✅ Testa conexão: `telnet localhost 5672`

---

### Problema 3: Mensagens vão direto para DLQ

**Sintomas:**
- Mensagens nunca são processadas
- Todas vão para DLQ imediatamente

**Soluções:**
1. ✅ Verifica se consumer está lançando exceção
2. ✅ Verifica `failure-strategy` no YAML (deve ser `reject` ou `requeue`)
3. ✅ Adiciona logs no consumer para debug

---

### Problema 4: Serialization error

**Sintomas:**
```
Failed to deserialize message: Unrecognized field "order_id"
```

**Soluções:**
1. ✅ Verifica se DTO tem `@JsonProperty` correto
2. ✅ Configura ObjectMapper para aceitar propriedades desconhecidas:
```java
@ApplicationScoped
@Produces
public ObjectMapper objectMapper() {
    return new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
}
```

---

## Melhores Práticas

### ✅ Configuração

```yaml
# BOM: Usar variáveis de ambiente
rabbitmq-host: ${RABBITMQ_HOST:localhost}
rabbitmq-port: ${RABBITMQ_PORT:5672}
rabbitmq-username: ${RABBITMQ_USERNAME:guest}
rabbitmq-password: ${RABBITMQ_PASSWORD:guest}

# RUIM: Hardcoded
rabbitmq-host: localhost
rabbitmq-password: secret123
```

### ✅ Naming Conventions

```yaml
# BOM: Nomes descritivos e hierárquicos
exchange: orders
queue: orders-processing-queue
routing-key: order.created.online

# RUIM: Nomes genéricos
exchange: ex1
queue: q
routing-key: msg
```

### ✅ Idempotência

```java
// BOM: Consumer idempotente
@Incoming("orders-in")
public Uni<Void> consumeOrder(OrderCreatedEvent event) {
    return orderRepository.findById(event.getOrderId())
        .onItem().transformToUni(existingOrder -> {
            if (existingOrder != null) {
                // Já processado - apenas ACK
                return Uni.createFrom().voidItem();
            }
            // Processa nova ordem
            return processNewOrder(event);
        });
}

// RUIM: Pode processar duplicatas
@Incoming("orders-in")
public void consumeOrder(OrderCreatedEvent event) {
    processOrder(event);  // Pode ser executado múltiplas vezes!
}
```

### ✅ Mensagens Estruturadas

```java
// BOM: DTO rico com metadata
public class OrderCreatedEvent {
    private UUID orderId;
    private UUID customerId;
    private Instant createdAt;
    private String eventVersion = "1.0";  // Versionamento
    private UUID correlationId;  // Rastreabilidade
    // ...
}

// RUIM: Mensagem pobre
public class OrderEvent {
    private String id;  // String ao invés de UUID
    private double amount;  // double ao invés de BigDecimal
    // Falta timestamp, correlationId, etc.
}
```

### ✅ Error Handling

```java
// BOM: Tratamento específico de erros
@Incoming("orders-in")
public Uni<Void> consume(Message<OrderCreatedEvent> message) {
    return processOrder(message.getPayload())
        .onFailure(ValidationException.class)
            .invoke(err -> logger.warn("Invalid order, sending to DLQ"))
            .onFailure(ValidationException.class)
            .recoverWithUni(err -> message.nack(err))  // DLQ
        .onFailure(TemporaryException.class)
            .retry().withBackOff(Duration.ofSeconds(1)).atMost(3)
        .onFailure()
            .invoke(err -> logger.error("Unexpected error", err))
            .recoverWithUni(err -> message.nack(err));
}

// RUIM: Captura tudo igual
@Incoming("orders-in")
public void consume(OrderCreatedEvent event) {
    try {
        processOrder(event);
    } catch (Exception e) {
        // Trata todos erros igualmente
        logger.error("Error", e);
    }
}
```

### ✅ Monitoring

```java
// BOM: Métricas + logs estruturados
@Incoming("orders-in")
public void consume(OrderCreatedEvent event) {
    Timer.Sample sample = Timer.start(registry);

    MDC.put("orderId", event.getOrderId().toString());
    MDC.put("correlationId", event.getCorrelationId().toString());

    try {
        processOrder(event);
        registry.counter("orders.processed", "status", "success").increment();
    } catch (Exception e) {
        registry.counter("orders.processed", "status", "error").increment();
        throw e;
    } finally {
        sample.stop(registry.timer("orders.processing.time"));
        MDC.clear();
    }
}
```

---

## Configuração Completa de Produção

```yaml
# application.yml - Produção
# RabbitMQ Connection
rabbitmq-host: ${RABBITMQ_HOST:rabbitmq.production.com}
rabbitmq-port: ${RABBITMQ_PORT:5672}
rabbitmq-username: ${RABBITMQ_USERNAME}
rabbitmq-password: ${RABBITMQ_PASSWORD}

mp:
  messaging:
    connector:
      smallrye-rabbitmq:
        host: ${rabbitmq-host}
        port: ${rabbitmq-port}
        username: ${rabbitmq-username}
        password: ${rabbitmq-password}
        # SSL/TLS
        ssl: true
        # Reconnection
        reconnect-attempts: 100
        reconnect-interval: 10
        # Connection timeout
        connection-timeout: 60000
        # Channel pool
        channel-pool-max-size: 20

    # Outgoing - Producer
    outgoing:
      orders-out:
        connector: smallrye-rabbitmq
        exchange:
          name: orders
          type: topic
          durable: true
          auto-delete: false
        default-routing-key: order.created
        confirmation-timeout: 10000
        max-inflight-messages: 1000

    # Incoming - Consumer
    incoming:
      orders-in:
        connector: smallrye-rabbitmq
        queue:
          name: orders-processing-queue
          durable: true
          auto-delete: false
          exclusive: false
          x-dead-letter-exchange: orders-dlx
          x-dead-letter-routing-key: orders.failed
          x-message-ttl: 3600000  # 1 hora
          x-max-length: 100000
        exchange:
          name: orders
          type: topic
          durable: true
        routing-keys: "order.#"
        prefetch: 50
        auto-acknowledgment: false
        failure-strategy: reject
        max-incoming-internal-buffer-size: 500

      # DLQ Consumer
      orders-dlq:
        connector: smallrye-rabbitmq
        queue:
          name: orders-dlq-queue
          durable: true
        exchange:
          name: orders-dlx
          type: topic
          durable: true
        routing-keys: "orders.failed"

# Quarkus configs
quarkus:
  # Logging
  log:
    level: INFO
    category:
      "io.smallrye.reactive.messaging":
        level: INFO
      "br.com.iagoomes":
        level: DEBUG
    console:
      format: "%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3.}] (%t) %s%e%n"

  # Health
  smallrye-health:
    ui:
      enable: true

  # Metrics
  micrometer:
    enabled: true
    export:
      prometheus:
        enabled: true
```

---

## Referências

- [Quarkus RabbitMQ Guide](https://quarkus.io/guides/rabbitmq)
- [SmallRye Reactive Messaging](https://smallrye.io/smallrye-reactive-messaging/)
- [RabbitMQ Documentation](https://www.rabbitmq.com/documentation.html)
- [MicroProfile Reactive Messaging](https://github.com/eclipse/microprofile-reactive-messaging)
- [RabbitMQ Patterns](https://www.rabbitmq.com/getstarted.html)
- [Reactive Streams](https://www.reactive-streams.org/)