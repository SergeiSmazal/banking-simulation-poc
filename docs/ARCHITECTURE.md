# Fintech Transaction Simulator — Architecture

> Portfolio project demonstrating Senior/Lead-level backend engineering skills
> Stack: Next.js · Spring Boot 3 · Apache Kafka · Redis · PostgreSQL · Spring AI

## 1. Project Idea

A banking transaction simulator that generates load "on the fly" and demonstrates,
in practice, how real distributed-systems problems are solved: concurrent access
to account balances, reliable message delivery, caching, and anomaly detection.

## 2. Architecture Map

```
┌─────────────┐      ①  POST /transfer        ┌──────────────────────┐
│   Next.js   │ ─────────────────────────────▶ │   Spring Boot API    │
│  Dashboard  │                                │   (Producer)         │
└─────────────┘ ◀───────── 202 Accepted ────── └───────────┬──────────┘
                                                           │ ② rate-limit check
                                                           ▼
                                                     ┌─────────────┐
                                                     │    Redis    │
                                                     │ (rate limit,│
                                                     │  balance    │
                                                     │  cache,     │
                                                     │ distributed │
                                                     │    lock)    │
                                                     └──────┬──────┘
                                                            │
                                                            ▼
                                                     ┌─────────────┐
                                                     │    Kafka    │
                                                     │  topic:     │
                                                     │ transactions│
                                                     │ (partition  │
                                                     │  key=accId) │
                                                     └──────┬──────┘
                              ┌─────────────────────────────┼─────────────────────┐
                              ▼                             ▼                     ▼
                     ┌─────────────────┐          ┌──────────────────┐   ┌─────────────────┐
                     │ Consumer:       │          │ Consumer:        │   │ Spring AI:      │
                     │ Processor       │          │ Anomaly Detector │   │ pattern analysis│
                     │ (idempotent,    │          │ (separate        │   │ → alert via     │
                     │ outbox pattern) │          │  consumer group) │   │  Redis Pub/Sub  │
                     └────────┬────────┘          └────────┬─────────┘   └─────────────────┘
                              ▼                            ▼
                     ┌─────────────────┐          ┌──────────────────┐
                     │  PostgreSQL     │          │  Redis Pub/Sub   │
                     │  (transactions, │          │  → WebSocket to  │
                     │   outbox_events)│          │   Next.js        │
                     └─────────────────┘          └──────────────────┘
```

## 3. Data Flow — Step by Step (with corrections)

1. **Next.js** sends `POST /api/transfer` with an amount and account ID.
2. **Spring Boot API (Producer)** checks the rate limit in Redis (`INCR` +
   `EXPIRE`, sliding window or token bucket — token bucket is the more common
   discussion point in interviews).
3. If the limit isn't exceeded, the API does **not write directly to Kafka**.
   Instead, in a single DB transaction it writes a row to the `transactions`
   table (status `PENDING`) and a row to the `outbox_events` table
   **(Transactional Outbox Pattern)**.
   This solves the classic dual-write problem: if you write to the DB and to
   Kafka as two independent actions, a failure between them can leave the
   data inconsistent.
4. A separate process (**Debezium**, or a simple polling publisher) reads
   `outbox_events` and publishes the message to the Kafka topic `transactions`,
   **partitioned by `accountId`** — this guarantees all transactions for a
   given account are processed strictly in order by a single consumer.
5. The API immediately returns `202 Accepted` to the frontend.
6. **Consumer (Processor)** reads the message. First it checks an
   **idempotency key** (the transaction ID itself) in Redis — if it's already
   been processed, it skips it. This guards against Kafka's at-least-once
   delivery semantics (a message can arrive twice during a rebalance).
7. Before updating the balance, the Consumer acquires a **distributed lock**
   in Redis (`SET key value NX PX 5000`, or via the Redisson library) scoped
   to the specific `accountId` — this eliminates the race condition that can
   occur if two messages for the same account are processed in parallel
   (multiple consumer instances).
8. It updates the balance in Redis (cache) and the transaction status in
   PostgreSQL (`COMPLETED`).
9. If processing fails, retry with exponential backoff; after N attempts the
   message goes to a **Dead Letter Topic (DLQ)** for manual review — in a
   banking system, transactions can't simply be "lost."
10. **Spring AI** listens to the same topic (a separate consumer group) and
    analyzes the stream for anomalies (unusually frequent transfers, atypical
    amounts). When one is detected, it publishes an alert via Redis Pub/Sub,
    which reaches the dashboard in real time over WebSocket.

## 4. Key Interview Topics This Demonstrates

| Topic | Where in the project |
|---|---|
| At-least-once delivery and idempotency | Consumer + idempotency key in Redis |
| Transactional Outbox | Producer, DB↔Kafka consistency |
| Race conditions under concurrent access | Distributed lock before balance update |
| Backpressure | Kafka as a buffer between API and processing |
| Partitioning and message ordering | partition key = accountId |
| Fault tolerance | Retry + DLQ |
| Caching and invalidation | Redis cache-aside for balances |
| Rate limiting | Redis token bucket at the API layer |
| Observability | Micrometer + Prometheus (see section 6) |
| AI in production | Spring AI, real-time stream analysis |

## 5. Tech Stack

- **Frontend:** Next.js 15, TypeScript, a simple WebSocket client for live alerts
- **Backend:** Spring Boot 3.3+, Java 21
- **Messaging:** Apache Kafka (KRaft mode, no Zookeeper — the 2026 standard)
- **Cache/Lock/PubSub:** Redis 7+ (or the Redisson client for locks)
- **DB:** PostgreSQL 16
- **AI:** Spring AI + a model of choice (via the Anthropic API or a local model)
- **Infrastructure:** Docker Compose for the local environment

## 6. Nice-to-Haves if Time Allows (interview bonus points)

- **Micrometer + Prometheus + Grafana** — metrics on consumer latency, topic lag.
- **Testcontainers** — integration tests with real Kafka/Postgres/Redis in CI.
- **Circuit Breaker (Resilience4j)** — for when Redis/the AI service is unavailable.
- **Schema Registry (Avro)** — to demonstrate a mature approach to message schema evolution.

## 7. Repository Structure

```
/frontend        — Next.js application
/backend
  /api           — Producer, REST controllers, rate limiting
  /processor     — Consumer, transaction processing logic
  /anomaly-ai    — Consumer + Spring AI integration
  /common        — shared DTOs, Kafka/Redis configuration
/docker-compose.yml
/docs
  ARCHITECTURE.md  — this file
```
