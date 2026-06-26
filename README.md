# pi-spring-ai

`pi-spring-ai` is a small, Pi-inspired coding agent harness built with Spring AI.
It focuses on a readable tool-use loop, safe workspace tools, event streaming,
and replayable sessions for Java/Spring developers.

한국어로는 Spring AI로 만든 작은 코딩 에이전트 하네스다. 단순 챗봇이 아니라, LLM이 안전한 도구를 호출하고 그 과정을 이벤트와 세션으로 남기는 구조를 학습하기 위한 프로젝트다.

## Repository Baseline

이 저장소의 제품/프로젝트 이름과 GitHub 저장소 이름은 모두 `pi-spring-ai`로 둔다. 문서와 코드의 애플리케이션 이름도 같은 이름을 기준으로 한다.

기준 문서는 다음 파일이다.

- `AGENTS.md`
- `docs/01_SOT.md`
- `docs/02_DESIGN.md`
- `docs/03_IMPLEMENTATION_PLAN.md`
- `docs/04_CODEX_TASK_PROMPTS.md`
- `docs/05_BLOG_SERIES_PLAN.md`

루트의 `01_SOT.md` 같은 중복 문서는 초기 작업 묶음의 사본이므로 추적하지 않는다. 앞으로 수정은 `docs/` 아래 문서에만 한다.

## Working Style

한 번에 하나의 마일스톤만 구현한다. 각 마일스톤은 다음 산출물을 남긴다.

- 작게 동작하는 코드 또는 실행 가능한 데모
- 외부 LLM API 없이 통과하는 테스트
- README 또는 docs의 짧은 변경 기록
- 블로그 글에 넣을 수 있는 의사결정 메모

커밋 단위도 마일스톤에 맞춘다. 예를 들어 `M0 Project bootstrap`, `M1 ChatClient hello`, `M4 Workspace guard`처럼 나눈다.

## MVP Order

1. M0 Project bootstrap
2. M1 ChatClient hello
3. M2 Agent domain model
4. M3 JSONL session store
5. M4 Workspace guard
6. M5 Read-only tools
7. M6 Tool execution policy
8. M7 Spring AI tool adapter
9. M8 AgentRuntime + SSE
10. M9 Write/Edit tools
11. M10 Bash tool
12. M11 README/demo polish

## Current Status

현재는 `M2 Agent domain model` 단계다. Spring Boot 애플리케이션 골격 위에 Spring AI `ChatClient`를 사용하는 최소 `/api/chat` endpoint가 있고, Gemini를 기본 chat provider로 둔 상태에서 OpenAI로도 전환할 수 있다.

M2에서는 아직 LLM tool calling, session store, AgentRuntime을 붙이지 않고 AgentRuntime 구현 전에 필요한 순수 도메인 타입만 추가했다.

- `AgentMessage`: `UserMessage`, `AssistantMessage`, `ToolResultMessage`
- `AgentEvent`: lifecycle, assistant, tool call, denial, error event
- `AgentRunOptions`: read-only safe defaults
- `AgentPromptCommand`: session/workspace/user prompt command
- `IdGenerator`: session/message/event/tool-call ID 생성

선택한 초기 버전과 의존성은 다음과 같다.

- Java 21 toolchain with `--release 21`
- Spring Boot 4.1.0
- Spring AI BOM 2.0.0
- Spring WebFlux: 이후 SSE endpoint를 위한 웹 기반
- Spring Boot Actuator: M0에서 `/actuator/health`로 애플리케이션 시작 상태를 확인하기 위한 최소 health endpoint
- Spring AI Google GenAI starter: Gemini를 기본 chat provider로 쓰기 위한 model starter
- Spring AI OpenAI starter: OpenAI provider 전환 예제를 유지하기 위한 model starter

아직 AgentRuntime, tool calling, session store는 구현하지 않았다. 이들은 M3 이후 단계에서 순서대로 붙인다.

## Run

```bash
./gradlew test
```

Windows PowerShell에서는 다음처럼 실행한다.

```powershell
.\gradlew.bat test
```

테스트는 실제 외부 LLM API를 호출하지 않는다. 컨트롤러 테스트는 fake model service를 사용하고, Spring context 테스트는 Google GenAI auto-configuration을 통과시키기 위한 테스트용 dummy API key만 사용한다.

## Chat API

실제 `/api/chat` endpoint는 기본적으로 Gemini Google GenAI provider를 사용한다. Gemini API key는 Google AI Studio에서 만들 수 있고, 로컬 실행 시 `GEMINI_API_KEY` 또는 `GOOGLE_API_KEY`로 전달한다.

```powershell
$env:GEMINI_API_KEY = "your-gemini-api-key"
$env:GEMINI_MODEL = "gemini-2.5-flash"
.\gradlew.bat bootRun
```

OpenAI로 전환하려면 provider와 OpenAI key를 같이 지정한다.

```powershell
$env:AGENT_MODEL_PROVIDER = "openai"
$env:OPENAI_API_KEY = "your-openai-api-key"
$env:OPENAI_MODEL = "gpt-4.1-mini"
$env:AGENT_MODEL_NAME = "gpt-4.1-mini"
.\gradlew.bat bootRun
```

요청 예시는 다음과 같다.

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/chat" `
  -ContentType "application/json" `
  -Body '{"text":"hello"}'
```

응답 형태:

```json
{"content":"..."}
```

다음 구현 작업은 `docs/04_CODEX_TASK_PROMPTS.md`의 `Task M3` 프롬프트를 기준으로 시작한다.
