# UPI Offline Mesh

A Spring Boot backend that demonstrates **offline, mesh-routed UPI-style payments** over a Bluetooth-like device-to-device network.

Imagine you're in a basement with zero connectivity. You send your friend **₹500**. Your phone encrypts the payment, broadcasts it to nearby phones, and the packet hops from device to device until one phone walks outside, gets 4G, and uploads it to this backend.

The backend then:

1. Verifies and decrypts the payment.
2. Rejects duplicates and replays.
3. Settles the payment exactly once.

This repository contains the **server side of that system**, plus a software simulator for the mesh network. You can run the entire flow on a single laptop without Bluetooth hardware, Android devices, or a database server.

> **Important:** This is a technical demonstration of **mesh-routed deferred settlement**, not a production implementation of offline UPI or UPI Lite.

---

## Table of Contents

* [What This Demo Proves](#what-this-demo-proves)
* [Quick Start](#quick-start)
* [The Demo Flow](#the-demo-flow)
* [Architecture](#architecture)
* [The Three Hard Problems](#the-three-hard-problems)
* [Project Structure](#project-structure)
* [API Reference](#api-reference)
* [Tests](#tests)
* [What's Not Real](#whats-not-real)
* [Honest Limitations](#honest-limitations)
* [Troubleshooting](#troubleshooting)

---

## What This Demo Proves

The system demonstrates three properties end to end:

### 1. Untrusted intermediaries can't read or modify payments

A payment can travel through arbitrary intermediate devices without exposing its contents.

**Hybrid RSA + AES-GCM encryption** provides:

* Confidentiality — intermediaries see only ciphertext.
* Integrity — tampering causes AES-GCM authentication to fail.
* Server-only decryption — only the backend holds the RSA private key.

### 2. Duplicate deliveries settle exactly once

The same payment can arrive at the backend through multiple bridge devices at almost exactly the same time.

The backend uses an atomic idempotency claim on the ciphertext hash:

```java
seen.putIfAbsent(packetHash, now);
```

Only the first delivery proceeds to decryption and settlement. Concurrent duplicates are dropped before they can touch the ledger.

### 3. Tampered and replayed packets are rejected

A modified ciphertext fails AES-GCM authentication.

A replayed, previously accepted packet has the same ciphertext hash and is rejected by the idempotency layer. Packets older than the configured freshness window are also rejected.

The dashboard lets you observe all three behaviors.

---

# Quick Start

## Prerequisites

You only need:

* **JDK 17 or newer**
* A terminal
* A network connection for the first Maven dependency download

Check your Java installation:

```bash
java -version
```

No separate Maven installation is required. The repository includes the Maven Wrapper.

---

## Run on Windows

Open a terminal in the project directory:

```powershell
.\mvnw.cmd spring-boot:run
```

The first run downloads Maven and the project dependencies. Subsequent starts are much faster.

---

## Run on macOS / Linux

```bash
./mvnw spring-boot:run
```

If the wrapper isn't executable:

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

---

## Open the Dashboard

Wait until you see something similar to:

```text
Started UpiMeshApplication in X.XXX seconds
```

Then open:

**[http://localhost:8080](http://localhost:8080)**

You'll get an interactive dashboard for driving the complete simulation.

---

## Stop the Server

Press:

```text
Ctrl+C
```

---

# The Demo Flow

The dashboard provides four operations that demonstrate the complete payment lifecycle.

## Step 1 — Compose a Payment

Choose:

* Sender
* Receiver
* Amount
* PIN

Then click **📤 Inject into Mesh**.

The backend simulates the sender's phone:

1. Creates a `PaymentInstruction`.
2. Generates a unique nonce and timestamp.
3. Encrypts the instruction using hybrid RSA + AES-GCM encryption.
4. Wraps the ciphertext in a `MeshPacket`.
5. Assigns an initial TTL of `5`.
6. Places the packet on `phone-alice`, an offline virtual device.

The dashboard should show `phone-alice` holding one packet.

---

## Step 2 — Run Gossip Rounds

Click **🔄 Run Gossip Round** twice.

Every virtual device holding a packet broadcasts it to every other device within simulated Bluetooth range.

For each hop:

* The packet is copied to neighboring devices.
* TTL decreases.
* The ciphertext remains unchanged.

In this simulator, every device is considered within range.

After the first round, multiple devices will hold the packet. After another round, the packet continues propagating while its TTL decreases.

In a real deployment, this propagation would happen organically as phones encounter one another.

---

## Step 3 — A Bridge Node Gets Internet

Click:

**📡 Bridges Upload to Backend**

`phone-bridge` is seeded as the device with:

```text
hasInternet = true
```

The simulator treats this as the phone walking outside the basement and getting 4G.

It uploads its packets to:

```text
POST /api/bridge/ingest
```

The backend pipeline is:

```text
SHA-256 ciphertext
       ↓
Idempotency claim
       ↓
RSA-OAEP + AES-GCM decryption
       ↓
Freshness validation
       ↓
Transactional settlement
       ↓
Ledger entry
```

Watch the dashboard:

* **Account Balances** — the sender is debited and receiver credited.
* **Transaction Ledger** — one settled transaction appears.

---

## Step 4 — Demonstrate Idempotency

The interesting case is when multiple bridge nodes deliver the same payment concurrently.

The included concurrency test simulates exactly this scenario:

```bash
.\mvnw.cmd test -Dtest=IdempotencyConcurrencyTest#singlePacketDeliveredByThreeBridgesSettlesExactlyOnce
```

It:

1. Creates one encrypted payment.
2. Starts three concurrent ingestion calls.
3. Delivers the same packet through all three.
4. Verifies that exactly one request settles.
5. Verifies that the other two are rejected as duplicates.
6. Verifies that the sender is debited exactly once.

Expected result:

```text
1 × SETTLED
2 × DUPLICATE_DROPPED
```

---

# Architecture

```text
┌─────────────────────────────────────────────────────────────────────┐
│                         SENDER PHONE                                │
│                           OFFLINE                                   │
│                                                                     │
│ PaymentInstruction                                                  │
│ { sender, receiver, amount, pinHash, nonce, signedAt }             │
│                         │                                           │
│                         ▼                                           │
│              Hybrid RSA + AES-GCM Encryption                        │
│                         │                                           │
│                         ▼                                           │
│ MeshPacket { packetId, ttl, createdAt, ciphertext }                 │
└─────────────────────────┬───────────────────────────────────────────┘
                          │
                          │ Bluetooth-style gossip
                          ▼
             ┌──────────────┐
             │  stranger 1  │
             └──────┬───────┘
                    │
                    ▼
             ┌──────────────┐
             │  stranger 2  │
             └──────┬───────┘
                    │
                    ▼
             ┌──────────────┐
             │    bridge    │ ◀── walks outside
             │  hasInternet │     gets 4G
             └──────┬───────┘
                    │
                    │ HTTPS POST
                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     SPRING BOOT BACKEND                             │
│                                                                     │
│ POST /api/bridge/ingest                                             │
│                         │                                           │
│                         ▼                                           │
│                  SHA-256(ciphertext)                                │
│                         │                                           │
│                         ▼                                           │
│             IdempotencyService.claim(hash)                          │
│                  atomic putIfAbsent                                 │
│                         │                                           │
│                  ┌──────┴──────┐                                    │
│                  │             │                                    │
│               duplicate      first                                  │
│                  │             │                                    │
│                  ▼             ▼                                    │
│               DROP      HybridCryptoService                         │
│                              decrypt                                │
│                                │                                    │
│                                ▼                                    │
│                         Freshness check                             │
│                                │                                    │
│                                ▼                                    │
│                       SettlementService                             │
│                         @Transactional                              │
│                                │                                    │
│                    ┌───────────┴──────────┐                         │
│                    ▼                      ▼                          │
│                 Accounts             Ledger                         │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

# The Three Hard Problems

## Problem 1 — Untrusted Intermediaries

A stranger's phone is carrying your payment.

How do we prevent that phone from reading or modifying the amount?

### Solution: Hybrid Encryption

The sender encrypts the payment using the server's public key. Intermediate devices only see opaque ciphertext.

RSA isn't suitable for encrypting arbitrary-sized JSON payloads, so the implementation uses the standard hybrid approach:

1. Generate a fresh AES-256 key.
2. Encrypt the payment JSON with AES-256-GCM.
3. Encrypt the AES key using RSA-OAEP.
4. Combine the encrypted key, IV, ciphertext, and authentication tag into one blob.

Conceptually:

```text
┌──────────────────────┬──────────┬────────────────────────────┐
│ RSA-encrypted AES key│  12-byte │ AES-GCM ciphertext + tag   │
│       256 bytes      │    IV    │                            │
└──────────────────────┴──────────┴────────────────────────────┘
```

AES-GCM provides authenticated encryption.

If an intermediate device changes even one bit of the ciphertext, authentication fails during decryption.

The server therefore gets both:

* **Confidentiality**
* **Integrity**

See:

```text
crypto/HybridCryptoService.java
```

---

## Problem 2 — The Duplicate Storm

Imagine three bridge nodes receive the same packet.

They all walk outside at roughly the same time and upload it within milliseconds:

```text
Bridge A ─┐
Bridge B ─┼──► /api/bridge/ingest
Bridge C ─┘
```

Without idempotency, the backend could debit ₹500 three times.

### Solution: Atomic Claim on the Ciphertext Hash

The first operation performed by the ingestion service is:

```java
String packetHash = sha256(ciphertext);

boolean first = seen.putIfAbsent(packetHash, now) == null;
```

`ConcurrentHashMap.putIfAbsent()` is atomic.

If 100 threads attempt to claim the same hash concurrently, exactly one becomes the first claimer.

The others are immediately classified as:

```text
DUPLICATE_DROPPED
```

### Why hash the ciphertext?

Not the `packetId`?

A malicious intermediary could potentially rewrite an outer packet identifier.

Not the plaintext?

That would require decrypting before deduplication.

The ciphertext is a better deduplication key because:

* It can be hashed without decrypting.
* AES-GCM detects tampering during decryption.
* A legitimate retransmission of the same packet has the same ciphertext.
* A separate payment has a different nonce and therefore a different ciphertext.

### Production equivalent

The demo uses:

```text
ConcurrentHashMap
```

A distributed deployment would use something equivalent to:

```text
Redis SET key NX EX 86400
```

The semantics are the same: atomically claim a key with an expiration time.

There's also a database-level defense:

```text
transactions.packet_hash UNIQUE
```

If the cache layer fails and two transactions somehow reach the ledger, the database rejects the duplicate.

---

## Problem 3 — Replay Attacks

Suppose an attacker captures a payment packet and tries to replay it days later.

The system uses two layers of defense.

### Freshness

The encrypted payment contains:

```text
signedAt
```

The backend rejects packets older than the configured freshness window, currently 24 hours.

An attacker cannot simply modify the timestamp because it is inside the authenticated AES-GCM payload.

### Idempotency

The payment also contains a unique:

```text
nonce
```

Two legitimate payments of ₹100 from Alice to Bob therefore produce different payloads and different ciphertext hashes.

But replaying the *same* packet produces the exact same ciphertext hash, which the idempotency layer recognizes.

See:

```text
service/BridgeIngestionService.java
```

---

# Project Structure

```text
upi-offline-mesh/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── README.md
│
├── src/
│   ├── main/
│   │   ├── resources/
│   │   │   ├── application.properties
│   │   └── templates/
│   │       └── dashboard.html
│   │
│   └── java/com/demo/upimesh/
│       │
│       ├── UpiMeshApplication.java
│       │
│       ├── model/
│       │   ├── Account.java
│       │   ├── AccountRepository.java
│       │   ├── Transaction.java
│       │   ├── TransactionRepository.java
│       │   ├── MeshPacket.java
│       │   └── PaymentInstruction.java
│       │
│       ├── crypto/
│       │   ├── ServerKeyHolder.java
│       │   └── HybridCryptoService.java
│       │
│       ├── service/
│       │   ├── DemoService.java
│       │   ├── VirtualDevice.java
│       │   ├── MeshSimulatorService.java
│       │   ├── IdempotencyService.java
│       │   ├── SettlementService.java
│       │   └── BridgeIngestionService.java
│       │
│       ├── controller/
│       │   ├── ApiController.java
│       │   └── DashboardController.java
│       │
│       └── config/
│           └── AppConfig.java
│
└── src/test/
    └── java/com/demo/upimesh/
        └── IdempotencyConcurrencyTest.java
```

## Key Classes

| File                          | Responsibility                               |
| ----------------------------- | -------------------------------------------- |
| `UpiMeshApplication.java`     | Spring Boot entry point                      |
| `Account.java`                | JPA account entity with optimistic locking   |
| `Transaction.java`            | Settled transaction ledger                   |
| `MeshPacket.java`             | Wire format for mesh packets                 |
| `PaymentInstruction.java`     | Decrypted payment payload                    |
| `ServerKeyHolder.java`        | Generates the demo RSA keypair               |
| `HybridCryptoService.java`    | RSA-OAEP + AES-256-GCM encryption/decryption |
| `DemoService.java`            | Seeds accounts and simulates a sender        |
| `VirtualDevice.java`          | Represents a simulated phone                 |
| `MeshSimulatorService.java`   | Simulates device-to-device gossip            |
| `IdempotencyService.java`     | JVM-local atomic duplicate protection        |
| `SettlementService.java`      | Transactional debit/credit/ledger operation  |
| `BridgeIngestionService.java` | Hash → claim → decrypt → validate → settle   |
| `ApiController.java`          | REST API                                     |
| `DashboardController.java`    | Serves the demo UI                           |

---

# API Reference

| Method | Endpoint             | Description                                 |
| ------ | -------------------- | ------------------------------------------- |
| `GET`  | `/`                  | Dashboard                                   |
| `GET`  | `/api/server-key`    | Server RSA public key                       |
| `GET`  | `/api/accounts`      | Accounts and balances                       |
| `GET`  | `/api/transactions`  | Last 20 transactions                        |
| `GET`  | `/api/mesh/state`    | Current virtual-device state                |
| `POST` | `/api/demo/send`     | Simulate sender and inject packet           |
| `POST` | `/api/mesh/gossip`   | Run one gossip round                        |
| `POST` | `/api/mesh/flush`    | Upload bridge packets                       |
| `POST` | `/api/mesh/reset`    | Reset mesh and idempotency state            |
| `POST` | `/api/bridge/ingest` | Production-shaped bridge ingestion endpoint |
| `GET`  | `/h2-console`        | H2 database console                         |

## Bridge Ingestion

```http
POST /api/bridge/ingest
Content-Type: application/json
X-Bridge-Node-Id: phone-bridge-42
X-Hop-Count: 3
```

Request:

```json
{
  "packetId": "550e8400-e29b-41d4-a716-446655440000",
  "ttl": 2,
  "createdAt": 1730000000000,
  "ciphertext": "base64-encoded-RSA-and-AES-blob"
}
```

Response:

```json
{
  "outcome": "SETTLED",
  "packetHash": "a3f8c9...",
  "reason": null,
  "transactionId": 42
}
```

Possible outcomes:

```text
SETTLED
DUPLICATE_DROPPED
INVALID
```

`reason` is populated when the packet is invalid.

`transactionId` is populated when the payment settles.

---

## H2 Console

Open:

```text
http://localhost:8080/h2-console
```

Connection details:

```text
JDBC URL: jdbc:h2:mem:upimesh
Username: sa
Password: <empty>
```

> The H2 console exists for demonstration and debugging. It would be disabled in a production deployment.

---

# Tests

Run the full test suite:

```bash
./mvnw test
```

Windows:

```powershell
.\mvnw.cmd test
```

The key tests cover:

### `encryptDecryptRoundTrip`

Verifies that encrypted payment instructions can be decrypted successfully.

### `tamperedCiphertextIsRejected`

Modifies the ciphertext and verifies that:

```text
BridgeIngestionService
        ↓
INVALID
```

No settlement occurs.

### `singlePacketDeliveredByThreeBridgesSettlesExactlyOnce`

The headline concurrency test.

Three threads deliver the same packet simultaneously and verify:

```text
1 × SETTLED
2 × DUPLICATE_DROPPED
```

The sender's balance changes exactly once.

Run it directly:

```powershell
.\mvnw.cmd test -Dtest=IdempotencyConcurrencyTest#singlePacketDeliveredByThreeBridgesSettlesExactlyOnce
```

---

# What's Not Real

This is a teaching/demo project. Several components are intentionally simplified.

| Demo                            | Production                                            |
| ------------------------------- | ----------------------------------------------------- |
| H2 in-memory database           | PostgreSQL/MySQL with appropriate HA strategy         |
| `ConcurrentHashMap` idempotency | Redis `SET NX EX` or equivalent distributed primitive |
| RSA key generated at startup    | HSM/KMS/Vault-backed private key                      |
| Server-side packet creation     | Android/Kotlin implementation on the sender device    |
| Software mesh simulator         | BLE GATT / Wi-Fi Direct / another real transport      |
| Local settlement service        | Integration with actual banking/NPCI infrastructure   |
| No bridge authentication        | Mutual TLS or signed bridge-node credentials          |
| Seeded demo accounts            | KYC'd users and real VPAs/accounts                    |
| Demo PIN handling               | Proper hardware-backed credential/PIN mechanisms      |
| H2 console available            | Disabled in production                                |
| No rate limiting                | Per-node and per-account rate limits                  |
| Console logging                 | Structured logging, monitoring, SIEM integration      |
| Single backend instance         | Horizontally scaled service                           |

The **cryptography, packet validation, idempotency strategy, concurrency test, and transactional settlement model** are intentionally shaped around real engineering concerns.

The surrounding infrastructure is simplified so the concept can run locally.

---

# Honest Limitations

The most important part of this project is understanding what it **doesn't** solve.

These aren't merely implementation bugs. They are fundamental challenges of trying to defer settlement when there is no connectivity anywhere in the chain.

## 1. The receiver can't know the sender has funds

If Alice gives Bob a phone displaying:

```text
₹500 sent
```

that isn't proof that ₹500 has actually settled.

The backend may eventually discover that Alice doesn't have sufficient funds and reject the transaction.

Bob has therefore received an **IOU**, not final settlement.

This is a fundamental problem with deferred settlement.

Real offline payment designs need some form of **pre-funded, securely constrained value** on the device to provide stronger offline guarantees.

---

## 2. Offline double-spending is possible

Suppose Alice has ₹500.

While offline, she sends:

```text
₹500 → Bob
```

Then walks somewhere else and sends:

```text
₹500 → Carol
```

The backend cannot know about the first payment while Alice is disconnected.

Whichever transaction reaches the backend first can win. The other must eventually be rejected.

Cryptography alone cannot solve this because the fundamental problem is the absence of a trusted, globally synchronized view of Alice's balance.

---

## 3. Real Bluetooth networking is hard

The demo treats "Bluetooth range" as:

```text
every virtual device can see every other device
```

Real mobile networking is much harder.

Challenges include:

* Android background execution restrictions.
* BLE connection lifecycle.
* iOS peripheral/background limitations.
* Device discovery.
* Battery consumption.
* Intermittent connections.
* NAT/network transitions.
* Malicious or unreliable peers.
* Store-and-forward routing.
* Duplicate packets.
* TTL and routing strategy.

The simulator intentionally skips these problems so the backend protocol can be demonstrated independently.

---

## 4. Privacy and metadata

Encryption prevents intermediate devices from reading the payment contents.

It doesn't necessarily hide the **existence** of the packet.

A carrier device could potentially observe:

* That a packet exists.
* When it was received.
* When it was forwarded.
* How often it was forwarded.
* Packet size and other metadata.

A real deployment would need to consider privacy, regulatory requirements, metadata minimization, and device-compromise scenarios.

---

## What This Project Should Be Called

For a college project, portfolio, or technical review, I'd describe this as:

> **Mesh-routed deferred payment settlement with end-to-end encryption and exactly-once processing.**

Rather than:

> "Real-time offline UPI."

That distinction makes the project's technical contribution clearer.

The interesting engineering here is the combination of:

* Hybrid authenticated encryption.
* Store-and-forward packet propagation.
* Replay protection.
* Concurrent idempotency.
* Exactly-once settlement semantics.
* Transactional ledger updates.

---

# Troubleshooting

## `java: command not found`

Install JDK 17 or newer.

On Windows, for example:

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

Then verify:

```bash
java -version
```

---

## Port 8080 is already in use

Change the port in:

```text
src/main/resources/application.properties
```

For example:

```properties
server.port=8081
```

Then open:

```text
http://localhost:8081
```

---

## The first Maven run takes a long time

That's expected.

The Maven Wrapper downloads Maven and the project's dependencies on the first run.

Subsequent launches should be significantly faster.

---

## `mvnw.cmd` is not recognized in PowerShell

Run it with the relative-path prefix:

```powershell
.\mvnw.cmd spring-boot:run
```

not:

```powershell
mvnw.cmd spring-boot:run
```

---

## Tests occasionally fail

The concurrency test intentionally exercises simultaneous ingestion.

If you see an intermittent failure:

```powershell
.\mvnw.cmd test -Dtest=IdempotencyConcurrencyTest
```

Run it several times and inspect the actual failure output.

If it consistently fails on your environment, the failure is worth investigating rather than simply increasing sleeps or removing concurrency from the test.

---

# Final Takeaway

This project is deliberately small enough to run on a laptop, but it demonstrates several real distributed-systems problems:

```text
Offline sender
     │
     ▼
Encrypted payment
     │
     ▼
Untrusted mesh
     │
     ▼
Multiple possible deliveries
     │
     ▼
Atomic idempotency
     │
     ▼
Authenticated decryption
     │
     ▼
Freshness + replay protection
     │
     ▼
Transactional settlement
     │
     ▼
Exactly-once ledger update
```

The mesh itself is simulated.

The core backend problems — **confidentiality, integrity, replay resistance, concurrent deduplication, and transactional settlement** — are the parts this repository is designed to make concrete.
