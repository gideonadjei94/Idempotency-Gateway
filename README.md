## 1. Architecture Design

### Sequence Flowchart
### What happens when there is a New request from a user?
```
CLIENT                    CONTROLLER                 IDEMPOTENCY SERVICE              REDIS                PAYMENT SERVICE
  │                            │                              │                         │                         │
  │  POST /process-payment     │                              │                         │                         │
  │  Idempotency-Key: abc123   │                              │                         │                         │
  │  {amount: 100, currency: GHS}                             │                         │                         │
  ├───────────────────────────>│                              │                         │                         │
  │                            │                              │                         │                         │
  │                            │ 1. Validate request          │                         │                         │
  │                            │    (amount > 0? currency valid?)                       │                         │
  │                            │                              │                         │                         │
  │                            │ 2. Call idempotencyService   │                         │                         │
  │                            ├─────────────────────────────>│                         │                         │
  │                            │                              │                         │                         │
  │                            │                              │ 3. Hash the request     │                         │
  │                            │                              │    (SHA-256)            │                         │
  │                            │                              │                         │                         │
  │                            │                              │ 4. Check Redis          │                         │
  │                            │                              ├────────────────────────>│                         │
  │                            │                              │    GET idempotency:abc123                         │
  │                            │                              │<────────────────────────┤                         │
  │                            │                              │    NULL (not found)     │                         │
  │                            │                              │                         │                         │
  │                            │                              │ 5. Acquire lock         │                         │
  │                            │                              ├────────────────────────>│                         │
  │                            │                              │ SET idempotency:lock:abc123 "PROCESSING"          │
  │                            │                              │<────────────────────────┤                         │
  │                            │                              │    OK (lock acquired)   │                         │
  │                            │                              │                         │                         │
  │                            │                              │ 6. Process payment      │                         │
  │                            │                              ├─────────────────────────────────────────────────>│
  │                            │                              │                         │    sleep(2000ms)        │
  │                            │                              │<─────────────────────────────────────────────────┤
  │                            │                              │    PaymentResponse      │                         │
  │                            │                              │                         │                         │
  │                            │                              │ 7. Save to Redis        │                         │
  │                            │                              ├────────────────────────>│                         │
  │                            │                              │ SET idempotency:abc123  │                         │
  │                            │                              │ {hash, response, status}│                         │
  │                            │                              │<────────────────────────┤                         │
  │                            │                              │    OK                   │                         │
  │                            │                              │                         │                         │
  │                            │                              │ 8. Release lock         │                         │
  │                            │                              ├────────────────────────>│                         │
  │                            │                              │ DEL idempotency:lock:abc123                       │
  │                            │                              │                         │                         │
  │                            │<─────────────────────────────┤                         │                         │
  │                            │    Return result             │                         │                         │
  │                            │                              │                         │                         │
  │<───────────────────────────┤                              │                         │                         │
  │  201 Created               │                              │                         │                         │
  │  X-Cache-Hit: false        │                              │                         │                         │
  │  {status: "Charged 100 GHS"}                              │                         │                         │
```
### What happens when there is a DUPLICATE request?
```
CLIENT                    CONTROLLER                 IDEMPOTENCY SERVICE              REDIS
  │                            │                              │                         │
  │  POST (SAME REQUEST AGAIN) │                              │                         │
  ├───────────────────────────>│                              │                         │
  │                            ├─────────────────────────────>│                         │
  │                            │                              │ 1. Hash request         │
  │                            │                              │                         │
  │                            │                              │ 2. Check Redis          │
  │                            │                              ├────────────────────────>│
  │                            │                              │    GET idempotency:abc123
  │                            │                              │<────────────────────────┤
  │                            │                              │    FOUND! {hash, response}
  │                            │                              │                         │
  │                            │                              │ 3. Compare hashes       │
  │                            │                              │    Match? YES ✓         │
  │                            │                              │                         │
  │                            │                              │ 4. Return cached response
  │                            │<─────────────────────────────┤    (NO payment processing!)
  │<───────────────────────────┤                              │                         │
  │  201 Created               │                              │                         │
  │  X-Cache-Hit: true  ← NOTICE THIS!                        │                         │
  │  {status: "Charged 100 GHS"} (SAME RESPONSE)              │


```

## 2.Setup Instructions

### Prerequisites
- Java 21 or higher
- Maven 3.6+ (or use the included `mvnw` wrapper)
- Docker Desktop (for running Redis)
- Git (for cloning the repository)

### Getting Started

### Step 1: Clone the Repository
```bash
git clone https://github.com/gideonadjei94/Idempotency-Gateway.git .
```
_move into project directory_
```bash
cd finsafe
```

### Step 2: Start Redis

```bash
docker-compose up -d
```

- Verify Redis is running:
```bash
docker ps
```

### You should see: idempotency-redis container running
 
```bash
docker exec -it idempotency-redis redis-cli ping
```

#### Expected output: PONG
### Step 3: Build and Run the Application
### Build the project
```bash
./mvnw clean install
```

### Run the application

```bash
./mvnw spring-boot:run
```

### The application will start on http://localhost:8082

Expected console output:

Started FinsafeApplication in 2.5 seconds
Tomcat started on port 8082

### Step 4: Test the API

```bash
curl -X POST http://localhost:8082/api/v1/process-payment \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-123" \
  -d '{"amount": 100.00, "currency": "GHS"}'

```
Expected response (after ~2 seconds):
```bash

{
  "status": "Charged 100.00 GHS",
  "amount": 100.00,
  "currency": "GHS",
  "transactionId": "TXN-...",
  "processedAt": "2026-02-21T10:00:00Z"
}
```

### Configuration

#### You can customize the application behavior by editing src/main/resources/application.yml:

#### Properties

#### _idempotency.key-ttl	3600,  How long (in seconds) to remember an idempotency key_

#### _idempotency.processing-delay,	2000	Simulated payment delay in milliseconds_

#### _rate-limit.max-requests	5	Maximum requests per minute per client_

#### _circuit-breaker.failure-rate-threshold	50_


## 3.API Documentation

### Endpoint Overview
The gateway exposes a single endpoint for processing payments with idempotency guarantees.

#### POST /api/process-payment
Process a payment request with exactly-once semantics.

### Request Headers

| Header            | Required | Description                                  | Example                                |
|------------------|----------|----------------------------------------------|----------------------------------------|
| Content-Type      | Yes      | Must be `application/json`                   | application/json                       |
| Idempotency-Key   | Yes      | A unique identifier for this payment attempt | 550e8400-e29b-41d4-a716-446655440000 |

> **Note:** The Idempotency-Key should be a unique string generated by the client. UUIDs are recommended.

### Request Body
```json id="request-body"
{
  "amount": 100.00,
  "currency": "GHS"
}

Field	Type	Required	Constraints	Description
amount	Decimal	Yes	0.01 upwards - The amount to charge
currency	String	Yes	3-letter ISO-4217	Currency code (e.g., GHS, USD)
Response Headers
Header	Description	Example
X-Cache-Hit	Indicates if response was served from cache	true/false
X-RateLimit-Limit	Maximum requests allowed per window	100
X-RateLimit-Remaining	Requests remaining in current window	95
X-RateLimit-Reset	Unix timestamp when rate limit resets	1708354260
Success Response (201 Created)

{
  "status": "Charged 100.00 GHS",
  "amount": 100.00,
  "currency": "GHS",
  "transactionId": "TXN-550e8400-e29b-41d4-a716-446655440000",
  "processedAt": "2026-02-21T10:00:00Z"
}

Response Scenarios
Scenario 1: First Request (Happy Path)
curl -X POST http://localhost:8082/api/process-payment \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: abc-123" \
  -d '{"amount": 100.00, "currency": "GHS"}'

Response: 201 Created

{
  "status": "Charged 100.00 GHS",
  "transactionId": "TXN-abc123...",
  ...
}

Response time: ~2 seconds (simulated)

X-Cache-Hit: false

Scenario 2: Duplicate Request (Idempotent Replay)
curl -X POST http://localhost:8082/api/process-payment \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: abc-123" \
  -d '{"amount": 100.00, "currency": "GHS"}'

Response: 201 Created (same response)

{
  "status": "Charged 100.00 GHS",
  "transactionId": "TXN-abc123...",  ← Same transaction ID!
  ...
}

Response time: ~5ms (instant, cached)

X-Cache-Hit: true

Result: Customer NOT charged twice

Scenario 3: Same Key, Different Payload (Conflict)
curl -X POST http://localhost:8082/api/process-payment \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: abc-123" \
  -d '{"amount": 500.00, "currency": "USD"}'

Response: 409 Conflict

{
  "status": 409,
  "error": "Conflict",
  "message": "Idempotency key already used for a different request body.",
  "path": "/api/v1/process-payment",
  "timestamp": "2026-02-21T10:00:05Z"
}

```
> **Why:** Same idempotency key cannot be reused for a different payment

Solution: Generate a new idempotency key

```bash
Error Responses
400 Bad Request - Validation Error
{
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "fieldErrors": [
    "amount: Amount must be greater than 0",
    "currency: Currency must be a valid 3-letter ISO-4217 code"
  ]
}

409 Conflict - Idempotency Conflict
{
  "status": 409,
  "error": "Idempotency Conflict",
  "message": "Idempotency key already used for a different request body."
}


429 Too Many Requests - Rate Limit Exceeded
Retry-After: 60
X-RateLimit-Remaining: 0
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded for client '192.168.1.1'. Retry after 60 seconds."
}

```


> Wait for Retry-After before retrying.

```bash
503 Service Unavailable - Circuit Breaker Open
{
  "status": 503,
  "error": "Service Unavailable",
  "message": "Service temporarily unavailable. Please retry in a few moments."
}
```

> Cause: Redis unavailable, circuit breaker open

Retry after short delay (30–60s)

```bash
504 Gateway Timeout - In-Flight Request Timeout
{
  "status": 504,
  "error": "Gateway Timeout",
  "message": "A request with idempotency key 'abc-123' is currently being processed. Please retry after a short delay."
}
```

> Cause: Concurrent request timed out

Retry with same idempotency key after few seconds

```bash
Testing Examples
Example 1: First Payment
curl -X POST http://localhost:8082/api/process-payment \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: payment-001" \
  -d '{"amount": 250.50, "currency": "GHS"}' \
  -v

Expected: 201 Created after 2 seconds, X-Cache-Hit: false

Example 2: Retry Same Payment (Network Issue)
curl -X POST http://localhost:8082/api/process-payment \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: payment-001" \
  -d '{"amount": 250.50, "currency": "GHS"}' \
  -v

Expected: 201 Created instantly (~5ms), X-Cache-Hit: true, same transactionId

Example 3: Invalid Request
curl -X POST http://localhost:8082/api/process-payment \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: payment-002" \
  -d '{"amount": -50.00, "currency": "INVALID"}' \
  -v

Expected: 400 Bad Request with validation errors
```


## 4.Design Decisions

This section explains the key technical choices made in building the idempotency gateway and the reasoning behind them.

> 1. Why Redis Instead of a Database?

**Decision**: Use Redis as the primary storage for idempotency records rather than a traditional database (PostgreSQL, MySQL, etc.).

**Reasoning**:

> _**Speed is critical for payment APIs:**
Payment systems handle thousands of requests per second. Redis operates in memory with sub-millisecond read/write times, while traditional databases add 10–50ms latency per request.
Built-in TTL: Idempotency keys expire after 1 hour automatically, preventing unlimited memory growth.
Atomic operations: Redis SETNX ensures safe concurrent writes without race conditions.
Trade-off accepted: Redis is volatile; data is lost on restart. Acceptable because keys are temporary and the actual payment records are stored in the payment processor._


> 2. Why SHA-256 for Request Hashing?

**Decision:** Hash request payloads using SHA-256 to detect duplicates.

**Reasoning:**

> _**Collision resistant**: Virtually impossible for different requests to produce the same hash.
Deterministic: Same input → same output, ensuring consistent idempotency checks.
Privacy: Original payment amounts aren’t stored; hash proves request identity.
Canonicalization: JSON keys are sorted before hashing to handle field-order differences._


> 3. Why Circuit Breaker Pattern?

**Decision:** Implement a circuit breaker to protect against Redis failures.

**Reasoning:**

> _**Prevents cascading failures:** If Redis is down, failing fast avoids blocking threads.
Self-healing: Circuit automatically tests Redis recovery in HALF-OPEN state.
Preserves responsiveness: Instant rejection (~5ms) instead of long timeouts (~2s).
Three states:
CLOSED: Normal operation, monitor failures.
OPEN: All requests rejected, circuit protects system.
HALF-OPEN: Test recovery; if successful → close circuit, otherwise reopen._

> 4. Why Separate Service Layers?

**Decision:** Split code into Controller → Service → Utility layers.

**Reasoning:**

> _**Single Responsibility:** Each class focuses on one task.
Testability & Maintainability: Layers can be tested and modified independently.
Reusability: Components like CircuitBreaker and IdempotencyService can protect other services._

> 5. Why Validation at the DTO Level?

**Decision:** Use Bean Validation (@NotNull, @DecimalMin, etc.) instead of manual checks.

**Reasoning:**

> _**Fail fast:** Invalid requests rejected before any business logic executes.
Consistency: Uniform error responses.
Security: Prevents injection or overflow attacks.
Self-documenting: Constraints visible in code._

> 6. Why Store Request Hash Instead of Full Payload?

**Decision:** Store only the SHA-256 hash in Redis, not the full JSON payload.

**Reasoning:**

> _**Storage efficiency:** Fixed 64-byte hash vs variable-size payloads.
Privacy & security: Sensitive data isn’t stored; hash proves request identity.
Sufficiency: Only need to answer “Is this request identical?”_

> 7. Why Explicit Request Bindings?

**Decision:** Use @RequestHeader and @RequestBody @Valid instead of HttpServletRequest.

**Reasoning:**

> _**Type safety & self-documenting:** Method signature clearly shows required headers and body.
Automatic error handling: Missing/invalid data automatically triggers 400 Bad Request.
Easier testing: No need to mock low-level request objects._


## 5.The Developer’s Choice: Rate Limiting & Circuit Breaker

For the “Developer’s Choice” feature, I added two essential safety mechanisms for production-grade Fintech systems: Rate Limiting and Circuit Breaker.

> Feature 1: Rate Limiting

What It Does:
Limits clients to 5 requests per minute per IP to prevent abuse.

Why Critical:

- Protects against DDoS attacks and buggy client retries.

- Controls infrastructure cost by limiting excessive Redis usage.

- Maintains fair access for all clients.

How It Works:

- Sliding window counter in Redis: rate_limit:<IP> 
- Increment counter on request; reject with 429 Too Many Requests if limit exceeded.
- Counter resets after 60 seconds.

Example:

> Client sends 5 requests in 1 minute → all processed (X-RateLimit-Remaining decreases)
6th request → 429 Too Many Requests
After 60 seconds → quota resets

Configuration (example):

- rate-limit:
- enabled: true
- max-requests: 5
- window-seconds: 60
- use-ip-address: true

> Feature 2: Circuit Breaker

What It Does:
Detects Redis outages and fails fast to prevent thread pool exhaustion.

Why Critical:

- Avoids cascading failures when Redis is down.

- Preserves responsiveness by instantly rejecting requests instead of waiting for timeouts.

- Self-healing: retries a few requests in HALF-OPEN state to test recovery.

Three states:

> CLOSED: Normal operation; monitor Redis failures.

> OPEN: All requests rejected immediately (503 Service Unavailable).

> HALF-OPEN: Test a few requests; if successful → CLOSED, else → OPEN again.

Configuration (example):

circuit-breaker:
- enabled: true
- failure-rate-threshold: 50
- minimum-number-of-calls: 5
- wait-duration-in-open-state-seconds: 60
- permitted-number-of-calls-in-half-open-state: 3
- Why Both Features Work Together

> Rate Limiting: protects against external threats (malicious/bad clients).

> Circuit Breaker: protects against internal failures (Redis outages).

> Defense-in-depth: Together, they ensure the system remains resilient, responsive, and cost-efficient under heavy traffic or failure conditions.

> This structure clearly separates technical design choices from developer-added production safety features, with emphasis on real-world Fintech scenarios and implementation reasoning.
