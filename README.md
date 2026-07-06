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

현재는 `M10 Bash tool` 단계까지 구현했다. Spring Boot 애플리케이션 골격 위에 Spring AI `ChatClient`를 사용하는 최소 `/api/chat` endpoint가 있고, Gemini를 기본 chat provider로 둔 상태에서 OpenAI로도 전환할 수 있다.

M2에서는 아직 LLM tool calling, session store, AgentRuntime을 붙이지 않고 AgentRuntime 구현 전에 필요한 순수 도메인 타입만 추가했다.

- `AgentMessage`: `UserMessage`, `AssistantMessage`, `ToolResultMessage`
- `AgentEvent`: lifecycle, assistant, tool call, denial, error event
- `AgentRunOptions`: read-only safe defaults
- `AgentPromptCommand`: session/workspace/user prompt command
- `IdGenerator`: session/message/event/tool-call ID 생성

M3에서는 API/SSE나 AgentRuntime에 연결하지 않고, 파일 기반 세션 저장소만 추가했다.

- `SessionStore`: 세션 생성, 조회, 메시지 append/load, 이벤트 append/load
- `JsonlSessionStore`: `.pi-spring-ai/sessions/{sessionId}/` 아래 `session.json`, `messages.jsonl`, `events.jsonl` 저장
- `messages.jsonl`: `kind` discriminator가 있는 flat concrete message JSON
- `events.jsonl`: `type` discriminator가 있는 flat concrete event JSON
- 알 수 없는 discriminator, malformed JSONL, 없는 session, sessionId path traversal, 손상된 `session.json`은 명시적으로 실패

M4에서는 아직 read/ls/find/grep 도구를 만들지 않고, 앞으로 파일 도구가 공통으로 사용할 workspace guard만 추가했다.

- `WorkspaceProperties`: `agent.workspace.root`를 절대경로로 정규화하고 read/list/grep 제한 기본값을 유지
- `SafePathResolver`: 사용자 입력 경로를 workspace root 안으로만 resolve
- `resolveExisting`: 존재하는 파일/디렉터리는 real path 기준으로 검사해서 symlink escape를 차단
- `resolveForFuturePath`: 나중에 write 계층에서 새 경로에만 쓸 수 있도록 존재하는 parent real path 기준 검증 제공
- `SecretFileGuard`: `.env`, `.env.*`, `*.pem`, `*.key`, `id_rsa`, `id_dsa`, `id_ecdsa`, `id_ed25519`, `.git`, `application-prod.yml`, `application-prod.yaml`, `application-prod.properties`, `secrets.*` 차단

선택한 초기 버전과 의존성은 다음과 같다.

- Java 21 toolchain with `--release 21`
- Spring Boot 4.1.0
- Spring AI BOM 2.0.0
- Spring WebFlux: 이후 SSE endpoint를 위한 웹 기반
- Spring Boot Actuator: M0에서 `/actuator/health`로 애플리케이션 시작 상태를 확인하기 위한 최소 health endpoint
- Spring AI Google GenAI starter: Gemini를 기본 chat provider로 쓰기 위한 model starter
- Spring AI OpenAI starter: OpenAI provider 전환 예제를 유지하기 위한 model starter

M5에서는 Spring AI Tool Calling에 연결하지 않고, 내부 read-only built-in tools만 추가했다.

- `AgentTool`: 내부 도구의 최소 계약. M5에서는 `execute(ToolArguments)` 형태로 시작
- `ToolSpec`, `ToolArguments`, `ToolResult`: 도구 설명, 입력, 결과 모델
- `ToolRegistry`: 등록된 도구 조회와 `AgentRunOptions` 기반 safety level 필터링
- `LsTool`: workspace 내부 디렉터리 목록. secret entry 제외, 항목 수와 출력 길이 제한
- `ReadTool`: workspace 내부 UTF-8 텍스트 파일 읽기. secret, traversal, large/binary file 차단
- `FindTool`: workspace 내부 파일 glob 검색. `.git`, `build`, `.gradle`, secret path 제외
- `GrepTool`: workspace 내부 텍스트 파일 literal substring 검색. secret 제외, large/binary file skip

M6에서는 Spring AI Tool Calling에 연결하지 않고, 내부 도구 실행 전후 정책과 공통 executor만 추가했다.

- `ToolSafetyLevel`: `READ_ONLY`, `WRITE`, `BASH` 등급
- `ToolExecutionContext`: 실행 시점의 `AgentRunOptions` 전달
- `ToolPolicyDecision`, `ToolPolicy`, `DefaultToolPolicy`: read-only 기본 허용, write/bash 명시 옵션 요구, 공통 output truncation
- `ToolExecutionService`: 정책 거부 시 실제 도구를 실행하지 않고 error `ToolResult` 반환, 허용 시 도구 실행 후 공통 후처리

M7에서는 내부 도구를 Spring AI Tool Calling에 직접 종속시키지 않고, 얇은 adapter로 연결했다.

- `SpringAiToolCallbackAdapter`: 내부 `AgentTool`을 Spring AI `ToolCallback`으로 노출
- `SpringAiToolCallbackFactory`: `AgentRunOptions`에 맞는 tool callback 목록 생성
- `SpringAiToolCallbackEvents`: tool start/finish/denied/failed 이벤트를 런타임에 연결할 수 있는 hook

M8에서는 세션 기반 prompt 처리와 SSE event stream을 추가했다.

- `AgentRuntime`, `DefaultAgentRuntime`: user message append, model stream, assistant/tool message append, event emit 조율
- `ModelClient`, `SpringAiModelClient`: runtime이 Spring AI `ChatClient`에 직접 묶이지 않도록 둔 얇은 model boundary
- `AgentController`: `POST /api/sessions`, `POST /api/sessions/{sessionId}/messages/stream`
- stream endpoint는 `agent_started`, `user_message_appended`, `assistant_delta`, `assistant_completed`, `agent_completed`와 tool event를 SSE로 보낸다.

M9에서는 명시적으로 `allowWrite=true`일 때만 실행되는 write/edit built-in tools를 추가했다.

- `WriteTool`: workspace 내부 UTF-8 텍스트 파일 생성과 명시적 overwrite 지원. secret, traversal, missing parent를 차단
- `EditTool`: workspace 내부 UTF-8 텍스트 파일에서 `oldText`가 정확히 한 번 매칭될 때만 치환. secret, traversal, large/binary file을 차단
- `ToolRegistry`: `allowWrite=true` 옵션에서 `write`, `edit` 도구를 활성화

M10에서는 명시적으로 `allowBash=true`일 때만 실행되는 `bash` built-in tool을 추가했다.

- `BashTool`: workspace root를 working directory로 고정하고 shell command를 실행. Windows에서는 `cmd.exe /c`, Unix 계열에서는 `/bin/sh -c`를 사용
- `BashCommandPolicy`: `rm -rf`, `git reset --hard`, `git clean -fd`, `shutdown`, `reboot`, `curl ... | sh`, `wget ... | sh`, secret path 참조를 기본 차단
- `ToolProperties`: `agent.tools.bash-timeout-seconds` 기본 30초와 `max-tool-output-chars` 기반 출력 제한 적용
- `ToolRegistry`: `allowBash=true` 옵션에서만 `bash` tool을 활성화

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

## Agent Session API

세션을 만들고, 같은 세션에 메시지를 보내면 agent event가 SSE로 흐른다.

```powershell
$session = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/sessions" `
  -ContentType "application/json" `
  -Body '{"workspaceRoot":"."}'

Invoke-WebRequest `
  -Method Post `
  -Uri "http://localhost:8080/api/sessions/$($session.sessionId)/messages/stream" `
  -ContentType "application/json" `
  -Headers @{ Accept = "text/event-stream" } `
  -Body '{"text":"프로젝트 구조를 간단히 설명해줘","allowWrite":false,"allowBash":false}'
```

SSE 응답은 다음 형태다.

```text
event:agent_event
id:e_...
data:{"type":"agent_started",...}
```

메시지와 이벤트는 `agent.session.root` 아래 JSONL 파일로 append된다. 테스트는 fake runtime/model을 사용하므로 실제 외부 LLM API를 호출하지 않는다.

Bash 실행은 기본적으로 비활성화되어 있다. 필요한 경우에만 request body에서 `allowBash:true`를 명시한다. 이때도 destructive command denylist, timeout, 출력 제한이 적용된다.

다음 구현 작업은 `docs/04_CODEX_TASK_PROMPTS.md`의 `Task M11` 프롬프트를 기준으로 시작한다.
