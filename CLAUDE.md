# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Personal RAG-based knowledge search system: automatically ingests GitHub issues and Notion notes, embeds them into a vector store, and answers natural-language questions (asked via Slack/KakaoTalk) using retrieval-augmented generation. Full design is in `나만의_RAG_이슈요약검색기_계획서.md` — read it for scenario-level detail beyond the summary below.

Current state: Phase 0–3 of the plan's roadmap are done — local infra (Postgres+pgvector, n8n) via `docker-compose.yml`, `/api/ingest` and `/api/query` implemented against Spring AI + pgvector, and an n8n workflow (`n8n/github-issue-ingest.workflow.json`) wiring a real GitHub repo webhook to `/api/ingest` via an ngrok tunnel to local n8n. Verified end-to-end against both GitHub's `ping` event and real `issues` webhook deliveries. The ngrok URL is session-scoped — it changes on tunnel restart, so the GitHub webhook's Payload URL needs re-registering each time (the plan's roadmap flags moving to a real deployment, e.g. a small VPS, to remove this). Slack/Kakao messenger integrations (Phase 4+) are not built yet.

## Commands

- Local infra: `docker compose up -d` (Postgres+pgvector on 5432, n8n on 5678)
- Build: `./gradlew build` (Windows: `gradlew.bat build`)
- Run: `./gradlew bootRun`
- Test all: `./gradlew test`
- Test a single class: `./gradlew test --tests "com.example.ragsearch.RagSearchApplicationTests"`
- Test a single method: `./gradlew test --tests "com.example.ragsearch.RagSearchApplicationTests.contextLoads"`

Toolchain: Java 26 (via Gradle toolchain, see `build.gradle`; `settings.gradle` has the foojay resolver so Gradle auto-downloads it if not installed — Gradle itself still needs a JVM 17+ on `JAVA_HOME`/`PATH` to run at all). Spring Boot 4.1.0, Spring AI 2.0.0 BOM, group `com.example`, root package `com.example.ragsearch`.

Embedding and chat models run locally via Ollama (`http://localhost:11434`, see `application.yaml`) — no API key required. Requires `ollama pull nomic-embed-text` (embedding, 768 dims) and `ollama pull llama3.2` (chat) once, and the Ollama service running, before `/api/ingest`/`/api/query` will work. Local datasource credentials (`ragsearch`/`ragsearch`) are hardcoded in `application.yaml` and only valid against the Docker Compose Postgres — not a production secret.

## Architecture

External automation (n8n) is meant to be the integration hub — it owns all webhook/polling/messenger plumbing and talks to this Spring Boot app only through two HTTP endpoints. This app itself never calls GitHub/Notion/Slack/Kakao APIs directly. n8n workflow exports live under `n8n/` in this repo (e.g. `n8n/github-issue-ingest.workflow.json`) and are imported into the n8n UI manually — n8n itself has no CLI-driven deploy step here.

**Data flow (implemented):**
- Ingest: `POST /api/ingest` {source, sourceId, title, url, content} (`IngestController` → `IngestService`) → delete any existing pgvector chunks for that `(source, sourceId)` via `FilterExpressionBuilder` → split content with `TokenTextSplitter` → `VectorStore.add(chunks)` (embeds + stores in one call). This is how re-ingesting an edited issue/note upserts instead of duplicating.
- Query: `POST /api/query` {question, topK?} (`QueryController` → `QueryService`) → `VectorStore.similaritySearch(SearchRequest)` for top-K chunks → join chunk text into a context block → `ChatClient` prompt constrained to "answer only from this context" → return `{answer, sources[]}` where sources are deduped `(title, url)` pairs pulled from chunk metadata.

**Package convention:** feature-first, then layer — `com.example.ragsearch.<feature>.{controller,service,dto}` (e.g. `ingest.controller.IngestController`, `ingest.service.IngestService`, `ingest.dto.IngestRequest`). Cross-feature shared code goes in `com.example.ragsearch.common`. New features (e.g. a future `notion` ingest path) should follow this same `feature/controller|service|dto` shape rather than a flat top-level `controller`/`service`/`dto` split.

**Component boundaries:**
- `com.example.ragsearch.common.DocumentMetadata` holds the metadata key constants (`source`, `sourceId`, `title`, `url`) shared between ingest (writes them) and query (reads them back for source attribution) — keep both sides using these constants rather than string literals so they can't drift.
- Spring AI's `VectorStore`/`EmbeddingModel`/`ChatModel` beans are auto-configured by the `spring-ai-starter-model-ollama` and `spring-ai-starter-vector-store-pgvector` starters from `application.yaml` — there's no manual `@Configuration` for them. `spring.ai.vectorstore.pgvector.initialize-schema: true` means the `vector_store` table/HNSW index are created automatically on startup; there's no separate migration. The pgvector column dimension (`spring.ai.vectorstore.pgvector.dimensions: 768`) must match the active embedding model's output size (`nomic-embed-text` = 768) — changing embedding models later means dropping and letting `vector_store` be recreated, not just editing the config.
- Kakao's chatbot skill server enforces a 5-second response timeout, which the query path (embedding + vector search + LLM call) will likely exceed once built — that integration needs an async/callback design (Phase 5, not started), not a synchronous n8n call. Slack has no such constraint, which is why Slack is the next messenger integration and Kakao is last.

**n8n workflow (`n8n/github-issue-ingest.workflow.json`):** `Webhook` node (path `github-issue`, `responseMode: onReceived` — acks GitHub immediately so events with no matching downstream branch, like GitHub's `ping`, don't surface as a failed delivery) → `IF` node filtering GitHub `issues` events to `action` in `opened`/`edited`/`reopened` (regex `^(opened|edited|reopened)$` — not a comma-separated list, n8n's IF regex operator doesn't do OR-by-comma) → `Set` node mapping the GitHub payload to `{source: "github", sourceId: "<owner>/<repo>#<issue number>", title, url, content: issue body}` → `HTTP Request` node `POST`ing to `http://host.docker.internal:8080/api/ingest` (n8n runs in Docker, so it must reach the host's Spring Boot process via `host.docker.internal`, not `localhost`). The GitHub repo webhook (Settings → Webhooks) points at `https://<ngrok-subdomain>.ngrok-free.dev/webhook/github-issue`, content type `application/json`, events limited to `Issues`.

**Not yet built** (per the plan's roadmap, in order): Slack slash command/mention → `/api/query` → Slack; Notion ingest; Kakao integration.
