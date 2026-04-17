# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

Spring Boot 3.4 (WebFlux, Reactor Netty) on Kotlin 2.2 / JVM 21. Hexagonal layout under base package `develop.x` (`src/main/kotlin/develop/x/`). `GET /sse?prompt=…` streams LLM tokens from a **local Ollama** instance via Koog 0.8 (`prompt-executor-ollama-client`). Defaults: `http://localhost:11434`, model `qwen3:14b` (see `application.yml`) — overridable via `application.yml` or `OLLAMA_BASE_URL` / `OLLAMA_MODEL` env vars (Spring relaxed binding). Ollama must be running locally (`ollama serve`) with the configured model pulled (`ollama pull qwen3:14b`) for `/sse` to work; otherwise expect a connection refused at request time, not at boot.

**Current endpoints:** `GET /` (greeting), `GET /sse?prompt=…` (SSE token stream).

**Known temporary state:** `KoogOllamaTokenStreamAdapter` is in diagnostic mode — it maps all Koog `StreamFrame` types to strings via `toString()` instead of filtering to text-only frames. This is intentional for debugging Ollama responses; it will be narrowed once the frame format is validated.

## Intended direction

A **streaming chat API on JetBrains Koog** (Kotlin agentic framework). Transport is **Server-Sent Events** (`text/event-stream`) — chosen over raw chunked / WebSocket because LLM token streaming is one-way and SSE gives free framing + auto-reconnect. The runtime is **Reactor Netty via Spring WebFlux**, so endpoint return types are `Flow<ServerSentEvent<…>>` and Koog's `Flow`-based streaming maps in with no glue.

## Architecture & code principles

**Hexagonal (ports & adapters).** Keep the domain pure and frameworks at the edges:

- `domain/` — pure Kotlin. Entities, value objects, domain services. No Spring, no Koog, no Ktor, no Reactor types.
- `application/port/inbound/` — interfaces the driving side calls (use case interfaces).
- `application/port/outbound/` — interfaces the application requires from the outside (implemented by driven adapters).
- `application/service/` — use case implementations. Pure Kotlin; coroutines / `Flow` allowed; **no Spring annotations**, no Koog imports. Wired via `@Bean` in `config/`.
- `adapter/inbound/web/` — driving adapters: WebFlux controllers (`@RestController`, SSE framing lives here). (`inbound`/`outbound` instead of `in`/`out` because `in` is a Kotlin keyword.)
- `adapter/outbound/llm/` — driven adapter wrapping Koog. `adapter/outbound/persistence/` for DB, etc. Adapters MAY use `@Component` — they are the framework edge.
- `config/` — Spring `@Configuration` that wires pure-Kotlin services from `application/service/` as beans implementing inbound ports. `BeanConfig` wires use cases; `OllamaConfig` creates the Koog `OllamaClient` and `LLModel` beans.

Dependency rule: arrows point **inward**. `adapter` → `application` → `domain`. Never the reverse. Domain must compile without any adapter on the classpath.

**SOLID + clean code are first-class concerns.** When in doubt:
- SRP — one reason to change per class. Split fat controllers / services aggressively.
- OCP / DIP — depend on the port (interface) defined by the inner layer, not the concrete adapter.
- ISP — narrow ports. A use case asks for `TokenStream`, not `KoogAgentClient`.
- LSP — substitutability matters for ports especially (test doubles must be drop-in).
- Naming reveals intent; functions do one thing; no comments restating code; small classes; deep modules with simple surfaces.
- Avoid leaking framework types across boundaries — no `ServerSentEvent`, `Mono`, `ResponseEntity` in `application/` or `domain/`.

When asked to add a feature, propose where it lives in the hexagon **first**, then implement.

## Toolchain

- Kotlin JVM 2.2.0, JVM toolchain 21 (Foojay resolver auto-provisions the JDK — no system JDK 21 required).
- Spring Boot 3.4.1 + `kotlin("plugin.spring")` (auto-`open` for Spring proxying).
- Koog 0.8.0 (`ai.koog:prompt-executor-ollama-client`, `ai.koog:prompt-model`). Koog APIs touching `kotlin.time.Clock` require `@OptIn(kotlin.time.ExperimentalTime::class)` at the call site (currently in `OllamaConfig` and `KoogOllamaTokenStreamAdapter`). See "Dependency version gotchas" below for version overrides required by the Kotlin 2.2 + Spring Boot 3.4.1 combination.
- Gradle wrapper is committed; always invoke builds via `./gradlew`, never a system `gradle`.
- Tests use `kotlin("test")` on the JUnit Platform (`useJUnitPlatform()`); use JUnit 5 / `kotlin.test` annotations. WebFlux endpoint tests should use `WebTestClient`. Test dependencies also include `reactor-test` for `StepVerifier`. No tests exist yet.

## Dependency version gotchas

Spring Boot 3.4.1's BOM pins older versions of several libraries that are incompatible with Kotlin 2.2. These are explicitly overridden in `build.gradle.kts`:

- **kotlin-stdlib**: `extra["kotlin.version"] = "2.2.0"` aligns the BOM's stdlib pin with the compiler.
- **kotlinx-coroutines**: forced to `1.10.2` (minimum that reads Kotlin 2.2 debug metadata v2).
- **kotlinx-serialization**: forced to `1.8.1` via `resolutionStrategy` (Koog's generated serializers need `typeParametersSerializers()` default method from 1.7+).
- **Ktor CIO engine**: `runtimeOnly("io.ktor:ktor-client-cio:3.2.2")` — Koog's internal `OllamaClient` uses Ktor `HttpClient` with `ServiceLoader` engine discovery; without an engine on the classpath you get a silent startup failure.

## Commands

```bash
./gradlew bootRun            # run the Spring Boot app (port 8080)
./gradlew build              # compile + test
./gradlew test               # run all tests
./gradlew test --tests "develop.x.FooTest"           # run one test class
./gradlew test --tests "develop.x.FooTest.barMethod" # run one test method
./gradlew clean
```

Smoke-test the streaming endpoint (requires `ollama serve` running):

```bash
curl -N 'http://localhost:8080/sse?prompt=Explain%20Kotlin%20coroutines%20briefly'
```

Test reports land in `build/reports/tests/test/index.html`.
