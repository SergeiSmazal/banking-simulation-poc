# 💳 Fintech Transaction Simulator

A project: a banking transaction simulator that demonstrates
production-grade fintech system architecture — message queues, caching,
data consistency, and real-time AI anomaly monitoring.

For the full architecture writeup and rationale behind each decision, see
[`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md).

## Stack

- **Frontend:** Next.js 15 + TypeScript
- **Backend:** Spring Boot 3, Java 21
- **Messaging:** Apache Kafka (KRaft)
- **Cache / Lock / Pub-Sub:** Redis 7
- **DB:** PostgreSQL 16
- **AI:** Spring AI (anomaly detection in the transaction stream)

## What This Project Demonstrates

- Transactional Outbox Pattern for DB↔Kafka consistency
- Idempotent message processing (at-least-once → effectively exactly-once)
- Distributed locking in Redis to prevent race conditions on balance updates
- API-level rate limiting (token bucket in Redis)
- Dead Letter Queue for messages that fail processing
- Kafka topic partitioning by `accountId` to guarantee ordering
- Anomaly detection via Spring AI + live alerts over WebSocket

## Quick Start

### 1. Start the infrastructure

```bash
docker compose up -d
```

This brings up Kafka (KRaft mode, no Zookeeper), Redis, and PostgreSQL.

### 2. Run the backend

```bash
cd backend
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080` by default.

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

The dashboard will be available at `http://localhost:3000`.

## Repository Structure

```
/frontend        — Next.js application (dashboard + WebSocket client)
/backend
  /api           — Producer, REST API, rate limiting
  /processor     — Consumer, transaction processing, distributed lock
  /anomaly-ai    — Consumer + Spring AI, anomaly detection
  /common        — shared DTOs and Kafka/Redis configuration
/docker-compose.yml
/docs
  ARCHITECTURE.md
```

## Environment Variables

Create a `.env` (or `application-local.yml`) with the following parameters:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/fintech
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
SPRING_REDIS_HOST=localhost
SPRING_REDIS_PORT=6379
```

## License

MIT — feel free to use this as a base for your own portfolio.
