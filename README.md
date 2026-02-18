## 1. Architecture Design

### System Flowchart
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


