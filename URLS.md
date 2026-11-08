# Quick Access URLs

## Running the Project

```bash
# Start all services (PostgreSQL, Redis, Kafka, Kafka UI, App)
docker compose up -d

# Stop all services
docker compose down

# View app logs
docker logs fdb-app -f

# Rebuild and restart app
docker compose build app && docker compose up -d app
```

---

## Frontend (Vite Dev Server)

```bash
cd frontend && npm run dev
```

| Service | URL |
|---------|-----|
| **Dashboard** | http://localhost:5173/ |
| **Alerts** | http://localhost:5173/alerts |

---

## Backend API (Spring Boot)

Base URL: `http://localhost:8080`

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/actuator/health` | GET | Health check (all components) |
| `/api/accounts` | GET | List all accounts with balances |
| `/api/transactions/history` | GET | Recent transactions (last 100) |
| `/api/transfer` | POST | Create transfer |
| `/api/simulator/start` | POST | Start load simulator |
| `/api/simulator/stop` | POST | Stop load simulator |
| `/api/simulator/toggle` | POST | Toggle simulator |
| `/api/simulator/status` | GET | Simulator status (RUNNING/STOPPED) |
| `/api/alerts/stream` | GET | SSE stream for anomaly alerts |

### Transfer Request Example

```bash
curl -X POST http://localhost:8080/api/transfer \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": "550e8400-e29b-41d4-a716-446655440000",
    "toAccountId": "550e8400-e29b-41d4-a716-446655440001",
    "amount": 100.00
  }'
```

---

## Kafka & Monitoring

| Service | URL | Description |
|---------|-----|-------------|
| **Kafka UI** | http://localhost:8081 | Web UI for Kafka topics, consumers, messages |
| **Kafka Broker** | localhost:9092 | Bootstrap server for producers/consumers |

### Kafka UI - Key Pages

| Page | What to See |
|------|-------------|
| **Topics → transactions** | All transfer messages, click to view JSON |
| **Topics → transactions.DLT** | Dead letter queue for failed messages |
| **Consumers → fdb-core-group** | Main transaction processor lag |
| **Consumers → fdb-anomaly-group** | Anomaly detector lag |
| **Brokers** | Broker health, partitions leadership |

### Useful Kafka Commands

```bash
# List topics
docker exec fdb-kafka kafka-topics.sh --bootstrap-server kafka:9092 --list

# View messages in transactions topic
docker exec fdb-kafka kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 \
  --topic transactions \
  --from-beginning \
  --property print.key=true \
  --property print.value=true

# Check consumer lag
docker exec fdb-kafka kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 \
  --group fdb-core-group \
  --describe
```

---

## Test Accounts (seeded via Flyway)

| ID | Owner | Initial Balance |
|----|-------|-----------------|
| 550e8400-e29b-41d4-a716-446655440000 | Alice Smith | 1000 EUR |
| 550e8400-e29b-41d4-a716-446655440001 | Bob Johnson | 2500.50 EUR |
| 550e8400-e29b-41d4-a716-446655440002 | Charlie Brown | 50 EUR |
| 550e8400-e29b-41d4-a716-446655440003 | Diana Prince | 10000 EUR |
| 550e8400-e29b-41d4-a716-446655440004 | Edward Norton | 0 EUR |

---

## Architecture Overview

```
┌─────────────┐     ┌─────────────┐     ┌─────────────────┐
│  Frontend   │────▶│  Backend    │────▶│  PostgreSQL     │
│  (Vite)     │     │  (Spring)   │     │  (Accounts,     │
│  :5173      │     │  :8080      │     │   Transactions) │
└─────────────┘     └──────┬──────┘     └─────────────────┘
                           │
                    ┌──────▼──────┐
                    │   Kafka     │
                    │  :9092      │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ Outbox   │ │ Core     │ │ Anomaly  │
        │ Publisher│ │ Consumer │ │ Detector │
        └──────────┘ └──────────┘ └──────────┘
```

---

## Anomaly Detection Rules

1. **High Amount**: Transfer > 50,000 EUR
2. **High Velocity**: > 5 transfers/minute from same account

Alerts published to Redis Pub/Sub channel `alerts` and streamed via SSE at `/api/alerts/stream`.

---

## Useful Scripts

```bash
# Run all tests
./mvnw test

# Build JAR
./mvnw package -DskipTests

# Frontend build
cd frontend && npm run build

# Frontend preview (production build)
cd frontend && npm run preview
```