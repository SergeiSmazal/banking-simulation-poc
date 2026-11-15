# 💳 Fintech Transaction Simulator

A banking transaction simulator that demonstrates production-grade fintech system
architecture — message queues, distributed locking, caching, idempotency, and
real-time rule-based anomaly monitoring.

For the full architecture writeup and rationale behind each decision, see
[`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md).

## Stack

- **Frontend:** Vite + React 18 + TypeScript (Recharts, Socket.IO client)
- **Backend:** Spring Boot 3.3, Java 21
- **Messaging:** Apache Kafka (KRaft, no Zookeeper)
- **Cache / Lock / Pub-Sub:** Redis 7 (+ Redisson for distributed locks)
- **DB:** PostgreSQL 16 (Flyway migrations)
- **Anomaly Detection:** Rule-based heuristics (velocity + high-amount checks) with Redis Pub/Sub
- **Observability:** Micrometer + Prometheus (+ Grafana dashboard)

## What This Project Demonstrates

- Transactional Outbox Pattern for DB↔Kafka consistency
- Idempotent message processing (at-least-once → effectively exactly-once)
- Distributed locking in Redis to prevent race conditions on balance updates
- API-level rate limiting (fixed-window counter in Redis)
- Dead Letter Queue (`transactions.DLT`) for messages that fail processing
- Kafka topic partitioning by `accountId` to guarantee per-account ordering
- Rule-based anomaly detection via a separate Kafka consumer group + live alerts over Redis Pub/Sub
- Custom Micrometer business metrics exported to Prometheus

## Quick Start

### 1. Start the infrastructure

```bash
docker compose up -d
```

This brings up PostgreSQL, Redis, Kafka (KRaft mode), Kafka UI, and Grafana.

| Service | URL |
|---------|-----|
| App (Spring Boot API) | http://localhost:8080 |
| Kafka UI | http://localhost:8081 |
| Grafana | http://localhost:3000 |

### 2. Run the backend

```bash
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`. See [`URLS.md`](./URLS.md) for the
full endpoint list.

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

The dashboard is available at `http://localhost:5173/` (alerts at `/alerts`).

## Repository Structure

```
/frontend             — Vite + React dashboard (dashboard + alerts pages)
/src/main/java/com/fdb/frankfurt
  /config             — Kafka, WebSocket configuration
  /controller         — REST controllers (transfers, accounts, simulator, alerts)
  /dto                — TransferRequest, AnomalyAlert
  /model              — Account, Transaction, OutboxEvent
  /repository         — Spring Data JPA repositories
  /service            — TransferService, OutboxPublisher,
                        TransactionProcessorListener, AnomalyDetectorListener,
                        SimulatorService, MetricsService
/src/main/resources    — application.yml, Flyway migrations
/src/test/java         — unit + Testcontainers integration tests
/docker-compose.yml
/docs
  ARCHITECTURE.md
URLS.md
```

## Environment Variables

Configuration is externalized in `application.yml` with sensible defaults:

```
DB_HOST=localhost          # PostgreSQL host
DB_USERNAME=bank_admin     # PostgreSQL user
DB_PASSWORD=bank_secure_password
REDIS_HOST=localhost       # Redis host
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

When running via Docker Compose these are overridden automatically (the service
names `postgres`, `redis`, `kafka` are used inside the compose network).

## Running Tests

```bash
./mvnw test
```

Unit tests run with H2 / embedded Redis. Integration tests
(`BankingIntegrationTest`) spin up real PostgreSQL, Redis, and Kafka containers
via Testcontainers.

## License

MIT — feel free to use this as a base for your own portfolio.