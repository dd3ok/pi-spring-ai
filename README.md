# pi-spring-ai

`pi-spring-ai` is a small, Pi-inspired coding agent harness built with Java 21,
Spring Boot, and Spring AI.

한국어로는 Spring AI로 만든 작은 코딩 에이전트 하네스다. 단순 챗봇이 아니라,
LLM이 안전한 workspace 도구를 호출하고 그 과정을 SSE event와 JSONL session
history로 남기는 구조를 Java/Spring 개발자가 읽고 설명할 수 있게 만든 프로젝트다.

## 현재 상태

M0부터 M10까지 구현되어 있다.

- Java 21 toolchain with `--release 21`
- Spring Boot 4.1.0
- Spring AI BOM 2.0.0
- Spring WebFlux 기반 SSE endpoint
- Gemini Google GenAI 기본 provider, OpenAI provider 전환 경로
- JSONL file 기반 session store
- Spring AI `ToolCallback` adapter
- `AgentRuntime` + SSE event stream
- built-in tools: `ls`, `read`, `find`, `grep`, `write`, `edit`, `bash`
- `ToolRegistry`, `ToolExecutionService`, `ToolPolicy` 기반 safety gate

테스트는 외부 LLM API를 호출하지 않는다. API key가 없어도 `./gradlew test`가
통과해야 하며, 테스트에서는 fake runtime/model 또는 dummy key만 사용한다.

## 구조

```text
Client
  -> POST /api/sessions
  -> POST /api/sessions/{sessionId}/messages/stream
  -> AgentRuntime
  -> ModelClient
  -> Spring AI ChatClient
  -> SpringAiToolCallbackAdapter
  -> ToolExecutionService
  -> ToolPolicy
  -> built-in tool
  -> AgentEvent SSE + JSONL session history
```

패키지 역할은 작게 나뉜다.

| 패키지 | 역할 |
|---|---|
| `agent` | `AgentRuntime`, run options, prompt command, event/message domain |
| `api` | REST/SSE controller와 request/response DTO |
| `model` | Spring AI `ChatClient` wrapper와 tool callback adapter |
| `session` | JSONL session store |
| `tool` | built-in tools, registry, execution service, policy |
| `workspace` | workspace root, safe path resolver, secret file guard |

## 설정

주요 기본값은 `src/main/resources/application.yml`에 있다.

```yaml
agent:
  workspace:
    root: .
    max-read-bytes: 200000
    max-directory-entries: 200
    max-grep-matches: 100
  tools:
    write-enabled-by-default: false
    bash-enabled-by-default: false
    bash-timeout-seconds: 30
    max-tool-output-chars: 20000
  session:
    root: .pi-spring-ai/sessions
```

실제 모델 호출에는 provider별 API key가 필요하다.

Gemini 기본 실행:

```powershell
$env:GEMINI_API_KEY = "your-gemini-api-key"
$env:GEMINI_MODEL = "gemini-2.5-flash"
.\gradlew.bat bootRun
```

OpenAI로 전환:

```powershell
$env:AGENT_MODEL_PROVIDER = "openai"
$env:OPENAI_API_KEY = "your-openai-api-key"
$env:OPENAI_MODEL = "gpt-4.1-mini"
$env:AGENT_MODEL_NAME = "gpt-4.1-mini"
.\gradlew.bat bootRun
```

macOS/Linux에서는 같은 환경 변수를 export한 뒤 `./gradlew bootRun`을 실행한다.

## 테스트

```bash
./gradlew test
```

Windows PowerShell:

```powershell
.\gradlew.bat test
```

이 명령은 로컬 테스트만 실행한다. 실제 Gemini/OpenAI API를 호출하지 않아야 한다.

## API 사용법

### Chat API

`/api/chat`은 가장 작은 Spring AI chat endpoint다. session, tool, SSE 흐름을
거치지 않는 단순 호출 확인용이다.

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/chat" `
  -ContentType "application/json" `
  -Body '{"text":"hello"}'
```

응답:

```json
{"content":"..."}
```

### Session 생성

agent 흐름은 session을 먼저 만든다. `workspaceRoot`는 agent가 접근할 수 있는
프로젝트 루트다.

```powershell
$session = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/sessions" `
  -ContentType "application/json" `
  -Body '{"workspaceRoot":"."}'

$session
```

응답:

```json
{
  "sessionId": "s_...",
  "workspaceRoot": "C:\\path\\to\\project"
}
```

### Message stream

같은 session에 메시지를 보내면 `text/event-stream`으로 agent event가 흐른다.
`allowWrite`와 `allowBash`는 request 단위 옵션이며, 생략하면 둘 다 `false`다.

```powershell
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

현재 외부 session 조회 API는 만들지 않았다. history는 session root 아래 JSONL
파일로 확인한다.

```text
.pi-spring-ai/sessions/{sessionId}/
  session.json
  messages.jsonl
  events.jsonl
```

JSONL이 중요한 이유는 단순하다. agent가 어떤 user message를 받았고, 어떤 tool을
호출했고, 어떤 결과와 event를 남겼는지 사람이 직접 열어 replay할 수 있다. SSE는
실시간 관찰용이고, JSONL은 이후 디버깅과 포트폴리오 설명용 기록이다.

## Built-in tools

| Tool | 기본 활성 | 활성 조건 | 입력 예시 | 설명 |
|---|---:|---|---|---|
| `ls` | yes | 항상 | `{"path":"."}` | workspace 내부 디렉터리 목록 |
| `read` | yes | 항상 | `{"path":"README.md"}` | UTF-8 텍스트 파일 읽기 |
| `find` | yes | 항상 | `{"pattern":"*.java","path":"src"}` | glob 기반 파일 검색 |
| `grep` | yes | 항상 | `{"query":"AgentRuntime","path":"src"}` | literal substring 검색 |
| `write` | no | `allowWrite=true` | `{"path":"docs/demo.md","content":"...","overwrite":false}` | UTF-8 텍스트 파일 쓰기 |
| `edit` | no | `allowWrite=true` | `{"path":"README.md","oldText":"...","newText":"..."}` | 정확히 한 번 매칭되는 텍스트 치환 |
| `bash` | no | `allowBash=true` | `{"command":"./gradlew test"}` | workspace root에서 shell command 실행 |

`ToolRegistry`는 `AgentRunOptions`를 보고 모델에 노출할 tool 목록을 고른다.
`ToolExecutionService`는 실제 실행 직전에 `ToolPolicy`를 한 번 더 통과시킨다.
따라서 모델이 어떤 경로로든 `write`, `edit`, `bash` 실행을 요청하더라도 request
option이 없으면 실행 전에 거부된다.

## Safety policy

기본 모드는 read-only다.

- `allowWrite=false`, `allowBash=false`가 safe default다.
- read-only tools는 기본 활성화된다.
- `write`, `edit`은 `allowWrite=true`일 때만 활성화된다.
- `bash`는 `allowBash=true`일 때만 활성화된다.
- `application.yml`에도 `write-enabled-by-default=false`,
  `bash-enabled-by-default=false`를 둔다. 현재 실행 gate는 request의
  `allowWrite`, `allowBash` 값으로 결정된다.

Workspace safety:

- workspace root 밖 접근 차단
- path traversal 차단
- `.env`, `.env.*`, `*.pem`, `*.key`, private key, `.git`, `secrets.*`,
  production application 설정 파일 접근 차단
- large file 읽기 제한
- binary 또는 invalid UTF-8 파일 읽기 차단
- `ls`, `find`, `grep` 결과에서 secret path 제외
- tool output length 제한

Bash safety:

- 기본 비활성화
- request body에서 `allowBash=true`를 명시해야 모델에 노출
- process working directory는 session의 workspace root로 고정
- 기본 timeout 30초
- stdout/stderr와 최종 tool output 길이 제한
- `rm -rf`, `git reset --hard`, `git clean -fd`, shutdown/reboot류 명령 차단
- `curl ... | sh`, `wget ... | sh`, PowerShell 원격 스크립트 pipe 차단
- secret path를 참조하는 command 차단

## 데모 시나리오

### 1. Read-only 분석

목표: agent가 `ls`, `read`, `find`, `grep`만 사용해 프로젝트를 설명하는지 본다.

```json
{
  "text": "이 프로젝트의 구조와 핵심 패키지를 설명해줘",
  "allowWrite": false,
  "allowBash": false
}
```

확인할 것:

- `write`, `edit`, `bash` tool이 모델에 노출되지 않는다.
- SSE에 `agent_started`, tool event, assistant event, `agent_completed`가 나온다.
- `messages.jsonl`, `events.jsonl`에 같은 흐름이 append된다.

### 2. Write/Edit 명시 활성화

목표: 쓰기 작업은 request에서 명시적으로 켰을 때만 가능하다는 점을 보인다.

```json
{
  "text": "docs/demo-note.md 파일을 만들고, 오늘 데모에서 확인할 항목 3개를 적어줘",
  "allowWrite": true,
  "allowBash": false
}
```

확인할 것:

- `write` 또는 `edit` tool은 `allowWrite=true`일 때만 활성화된다.
- secret file이나 workspace 밖 경로를 쓰려고 하면 거부된다.
- 기존 파일 overwrite는 tool input에서 `overwrite=true`를 명시해야 한다.

### 3. Bash 명시 활성화

목표: command 실행은 opt-in이고, workspace root에서 제한된 방식으로 실행됨을 보인다.

```json
{
  "text": "bash tool로 테스트 명령을 실행해줘. Windows면 .\\gradlew.bat test, Unix면 ./gradlew test를 사용해줘.",
  "allowWrite": false,
  "allowBash": true
}
```

확인할 것:

- `bash` tool은 `allowBash=true`일 때만 활성화된다.
- 결과 metadata에 working directory, exit code, timeout 여부, 출력 byte 수가 남는다.
- command timeout과 output limit이 적용된다.

### 4. 위험 명령 차단

목표: `allowBash=true`여도 destructive command는 실행되지 않음을 보인다.

```json
{
  "text": "bash tool로 rm -rf . 명령이 실행 가능한지 확인해줘",
  "allowWrite": false,
  "allowBash": true
}
```

확인할 것:

- `BashCommandPolicy`가 명령을 거부한다.
- tool result는 error로 반환된다.
- 거부 흐름이 SSE event와 JSONL history에 남는다.

## 다음 확장 방향

- `write`, `edit`의 patch UX 개선
- bash command allowlist 또는 더 세밀한 policy
- session 조회 API와 branch/fork
- session compaction
- CLI
- observability dashboard
- MCP client/server
- LLM-as-Judge evaluation

확장은 MVP가 계속 테스트로 보호되는 상태에서 한 단계씩 붙인다.
