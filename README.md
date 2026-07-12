# my-issue-rag-search

GitHub 이슈와 Notion 메모에 흩어진 개인 지식(기술 메모, 트러블슈팅 기록)을 자동으로 수집·임베딩해서 벡터 DB에 쌓아두고, 자연어 질문으로 과거 기록을 검색·요약해주는 개인용 RAG(Retrieval-Augmented Generation) 시스템입니다.

전체 설계 배경과 시나리오는 [`나만의_RAG_이슈요약검색기_계획서.md`](./나만의_RAG_이슈요약검색기_계획서.md)에 정리돼 있습니다.

## 진행 상태

계획서 로드맵 기준 **Phase 0~4 완료** (+ 계획에 없던 Discord 연동 추가):

- ✅ Phase 0~2: 로컬 인프라(Postgres+pgvector, n8n) 구성, `/api/ingest`·`/api/query` API 구현
- ✅ Phase 3: n8n으로 실제 GitHub 저장소 Webhook 연동 (ngrok 터널 경유)
- ✅ Phase 3.5 (계획 외 추가): Discord 봇으로 `/api/query` 질의응답 — 원래 계획은 슬랙/카카오톡이었지만 Discord를 먼저 연결함
- ✅ Phase 4: n8n으로 실제 Notion Database 폴링·수집 연동
- ⬜ Phase 5+: Slack 연동, Kakao 연동 (미착수)

## 아키텍처

```
GitHub Issues ─┐
               │ (n8n: Webhook)
Notion 메모  ──┼──────────────► n8n (수집 자동화) ──► POST /api/ingest ──► pgvector
               │
Discord 사용자 ◄── discord-bot(독립 프로세스) ◄── POST /api/query ◄── 질문
```

- **n8n**이 GitHub/Notion 수집을 전담합니다 (Webhook/폴링 → `/api/ingest`).
- **Discord 질의응답은 n8n을 거치지 않습니다.** n8n의 Discord 노드는 메시지 발신만 지원하고 수신용 Trigger가 없어서, `discord-bot/`의 독립 Node.js(discord.js) 프로세스가 상시 연결을 유지하며 직접 `/api/query`를 호출합니다.
- 이 Spring Boot 앱은 GitHub/Notion/Discord API를 직접 호출하지 않고, `POST /api/ingest`·`POST /api/query` 두 개의 HTTP 엔드포인트만 외부에 노출합니다.
- n8n 워크플로 정의는 `n8n/` 폴더에 JSON으로 export되어 있으며, n8n UI에서 **Import from File**로 불러와 사용합니다.

## 기술 스택

| 영역 | 기술 |
|---|---|
| 백엔드 | Spring Boot 4.1.0, Spring AI 2.0.0, Java 26 |
| 벡터 DB | PostgreSQL + pgvector (HNSW 인덱스, 코사인 거리) |
| 임베딩/챗 모델 | Ollama 로컬 실행 — `nomic-embed-text`(임베딩, 768차원), `qwen2.5:14b`(챗) |
| 자동화 허브 (수집) | n8n (Docker) |
| 메신저 봇 (질의응답) | Discord — discord.js 기반 독립 Node.js 프로세스 |
| 빌드 | Gradle (wrapper 포함) |

임베딩·챗 모델을 로컬 Ollama로 돌리기 때문에 **OpenAI 등 외부 LLM API 비용이 전혀 들지 않습니다.**

## 시작하기

### 1. 사전 준비

- JDK 17 이상 (Gradle 자체 실행용) — 프로젝트 빌드에는 Java 26 툴체인을 Gradle이 자동으로 받습니다
- Docker Desktop
- [Ollama](https://ollama.com) 설치 후 모델 2개 받기:
  ```bash
  ollama pull nomic-embed-text
  ollama pull qwen2.5:14b
  ```

### 2. 로컬 인프라 실행

```bash
docker compose up -d
```

Postgres(pgvector, 5432)와 n8n(5678)이 뜹니다.

### 3. 앱 실행

```bash
./gradlew bootRun
# Windows: gradlew.bat bootRun
```

`http://localhost:8080`에서 API가 열립니다. `vector_store` 테이블/인덱스는 첫 실행 시 자동 생성됩니다.

### 4. 테스트

```bash
./gradlew test
```

## API

### `POST /api/ingest`

이슈/메모 하나를 청크로 쪼개 임베딩하고 벡터 DB에 저장(같은 `source`+`sourceId`가 있으면 삭제 후 재삽입 = upsert)합니다.

```json
{
  "source": "github",
  "sourceId": "owner/repo#42",
  "title": "로그인 시 500 에러 발생",
  "url": "https://github.com/owner/repo/issues/42",
  "content": "이슈 본문 전체 텍스트..."
}
```

### `POST /api/query`

질문을 임베딩해 유사도 검색 후, 검색된 내용만으로 답변을 생성합니다.

```json
{ "question": "로그인할 때 500 에러가 나는 원인이 뭐야?", "topK": 5 }
```

```json
{
  "answer": "세션 저장소(Redis) 연결 타임아웃이 원인입니다.",
  "sources": [{ "title": "로그인 시 500 에러 발생", "url": "https://..." }]
}
```

## n8n 워크플로

`n8n/` 폴더의 워크플로는 n8n UI에서 **Import from File**로 불러온 뒤, 필요한 Credential(Notion 토큰 등)을 노드에 연결하고 **Publish(활성화)**해야 동작합니다.

- **`github-issue-ingest.workflow.json`** — GitHub 저장소 Webhook(issues 이벤트) → `/api/ingest`. 로컬 n8n을 외부에 노출하려면 ngrok 같은 터널이 필요합니다(무료 ngrok URL은 세션마다 바뀌므로 재부팅 시 GitHub Webhook URL 재등록 필요).
- **`notion-note-ingest.workflow.json`** — 5분마다 Notion Database를 폴링해서 변경된 페이지만 `/api/ingest`로 전송. Notion 인테그레이션 토큰은 워크플로 파일이 아니라 n8n의 Header Auth Credential로 별도 관리합니다.

## Discord 봇

`discord-bot/`은 n8n과 별개로 상시 실행되는 독립 프로세스입니다. DM 또는 서버에서 멘션된 메시지를 받아 `/api/query`에 질문을 전달하고, 답변과 출처를 그대로 Discord에 회신합니다.

```bash
cd discord-bot
npm install
cp .env.example .env   # DISCORD_BOT_TOKEN 채우기
npm start
```

- Discord Developer Portal에서 봇 생성 후 **Message Content Intent**를 반드시 켜야 합니다 — 꺼져 있으면 에러 없이 메시지를 그냥 무시합니다.
- `claude-hermes`(이 프로젝트 저장소에서 Claude Code를 원격 조종하기 위한 별도 봇)와는 다른 Discord 애플리케이션/토큰을 씁니다. 용도가 다른 두 봇을 굳이 섞지 않았습니다.

## 자세한 내용

프로젝트 구조, 컴포넌트 경계, 알려진 제약사항 등 더 자세한 개발자용 문서는 [`CLAUDE.md`](./CLAUDE.md)를 참고하세요.
