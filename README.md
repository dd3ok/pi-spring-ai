# pi-spring-ai

`pi-spring-ai` is a small, Pi-inspired coding agent harness built with Java 21,
Spring Boot, and Spring AI.

한국어로는 Spring AI로 만든 작은 코딩 에이전트 하네스다. 단순 챗봇이 아니라,
LLM이 안전한 workspace 도구를 호출하고 그 과정을 SSE event와 JSONL session
history로 남기는 구조를 Java/Spring 개발자가 읽고 설명할 수 있게 만든 프로젝트다.

## 현재 상태

M0부터 M15까지 구현되어 있다.

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
- Spring AI MCP client를 통한 opt-in external tool 연결
- Spring AI MCP server를 통한 built-in tool의 opt-in 노출

테스트는 외부 LLM API를 호출하지 않는다. API key가 없어도 `./gradlew test`가
통과해야 하며, 테스트에서는 fake runtime/model 또는 dummy key만 사용한다.

## 구조

```text
Client
  -> POST /api/sessions
  -> GET /api/sessions/{sessionId}
  -> POST /api/sessions/{sessionId}/messages/stream
  -> AgentRuntime
  -> ModelClient
  -> Spring AI ChatClient
  -> request-scoped ToolCallbackProvider
     -> built-in AgentTool
     -> Spring AI MCP ToolCallback -> McpAgentTool
  -> ToolRegistry
  -> SpringAiToolCallbackAdapter
  -> ToolExecutionService
  -> ToolPolicy
  -> built-in tool or MCP server
  -> AgentEvent SSE + JSONL session history

External MCP client
  -> POST/GET /mcp (Streamable HTTP)
  -> McpServerToolCallbackProvider
  -> SpringAiToolCallbackAdapter
  -> ToolExecutionService / ToolPolicy
  -> configured workspace의 built-in tool
```

패키지 역할은 작게 나뉜다.

| 패키지 | 역할 |
|---|---|
| `agent` | `AgentRuntime`, run options, prompt command, event/message domain |
| `api` | REST/SSE controller와 request/response DTO |
| `model` | Spring AI `ChatClient` wrapper와 tool callback adapter |
| `mcp` | Spring AI MCP client bridge와 built-in tool server exposure adapter |
| `session` | JSONL session store |
| `tool` | built-in tools, registry, execution service, policy |
| `workspace` | workspace root, safe path resolver, secret file guard |

## 설정

주요 기본값은 `src/main/resources/application.yml`에 있다.

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: false
        type: SYNC
        request-timeout: 20s
      server:
        enabled: false
        protocol: STREAMABLE
        type: SYNC
        request-timeout: 35s
        expose-mcp-client-tools: false

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
  mcp:
    allowed-tools: []
    server:
      exposed-tools: [ls, read, find, grep]
      allow-write: false
      allow-bash: false
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

### MCP client 설정

MCP client는 기본 비활성화다. Spring AI의 WebFlux MCP client starter가 연결과 tool
discovery를 담당하고, 발견한 tool은 기존 `ToolRegistry`, `ToolPolicy`, event/session
경계를 통과한다.

예를 들어 Streamable HTTP MCP server가 `http://localhost:3001/mcp`에서 실행 중이고,
server info name이 `demo`, tool name이 `echo`라면 다음처럼 실행할 수 있다.

```powershell
$env:AGENT_MCP_ENABLED = "true"
$env:AGENT_MCP_ALLOWED_TOOLS = "demo/echo"
.\gradlew.bat bootRun --args="--spring.ai.mcp.client.streamable-http.connections.demo.url=http://localhost:3001"
```

허용 목록은 MCP connection 설정 이름이 아니라 server가 initialize 응답으로 제공한
`serverName/toolName` 형식이다. 위 tool은 모델에 `mcp_demo_echo`라는 이름으로 노출된다.
연결을 활성화해도 request에서 `allowMcp=true`를 보내지 않으면 MCP tool은 노출되지 않는다.

### MCP server 설정

MCP server도 기본 비활성화다. 활성화하면 기존 WebFlux 애플리케이션의 `/mcp`에
Streamable HTTP endpoint가 추가된다. REST/SSE API를 대체하지 않으며 기본 주소는
`127.0.0.1`이다.

Read-only server 실행:

```powershell
$env:GEMINI_API_KEY = "not-used-by-mcp-server-demo"
$env:AGENT_MCP_SERVER_ENABLED = "true"
$env:SERVER_ADDRESS = "127.0.0.1"
.\gradlew.bat bootRun
```

현재 애플리케이션은 시작할 때 `ChatModel` bean도 생성하므로 값이 비어 있지 않은 key가
필요하다. 위 dummy 값은 MCP server tool 호출에서는 사용되지 않으며, agent message API로
모델을 호출하면 안 된다. 실제 agent 실행에는 유효한 provider API key를 사용한다.

MCP client에는 `http://127.0.0.1:8080/mcp`를 Streamable HTTP server URL로 등록한다.
사용자 API key 없이 실제 transport와 read-only tool 호출을 확인하려면 다음 통합 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.example.pispringai.mcp.McpServerIntegrationTest"
```

`write`와 `edit`을 노출하려면 allowlist와 write 권한을 함께 설정해야 한다.

```powershell
$env:AGENT_MCP_SERVER_EXPOSED_TOOLS = "ls,read,find,grep,write,edit"
$env:AGENT_MCP_SERVER_ALLOW_WRITE = "true"
```

`bash`도 같은 방식으로 `exposed-tools`에 추가하고
`AGENT_MCP_SERVER_ALLOW_BASH=true`를 설정해야 한다. 권한 flag 없이 privileged tool을
allowlist에 넣거나, 존재하지 않는 tool 이름을 지정하면 애플리케이션이 시작되지 않는다.
서버 tool의 workspace는 MCP request가 정하지 못하며 `agent.workspace.root`에 고정된다.
원격 공개와 인증은 M15 범위가 아니므로 인터넷에 직접 노출하지 않는다.

## 테스트

```bash
./gradlew test
```

Windows PowerShell:

```powershell
.\gradlew.bat test
```

이 명령은 로컬 테스트만 실행한다. 실제 Gemini/OpenAI API나 원격 MCP server를 호출하지 않아야 한다.

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
`allowWrite`, `allowBash`, `allowMcp`는 request 단위 옵션이며, 생략하면 모두 `false`다.

```powershell
Invoke-WebRequest `
  -Method Post `
  -Uri "http://localhost:8080/api/sessions/$($session.sessionId)/messages/stream" `
  -ContentType "application/json" `
  -Headers @{ Accept = "text/event-stream" } `
  -Body '{"text":"프로젝트 구조를 간단히 설명해줘","allowWrite":false,"allowBash":false,"allowMcp":false}'
```

SSE 응답은 다음 형태다.

```text
event:agent_event
id:e_...
data:{"type":"agent_started",...}
```

### Session history 조회

M13부터는 같은 session의 metadata, messages, events를 API로도 조회할 수 있다.

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/sessions/$($session.sessionId)"
```

응답은 replay/debug에 필요한 세 가지를 함께 돌려준다.

```json
{
  "session": {
    "id": "s_...",
    "workspaceRoot": "C:\\path\\to\\project",
    "createdAt": "...",
    "updatedAt": "...",
    "title": null
  },
  "messages": [
    {"kind": "user", "text": "..."},
    {"kind": "tool_result", "toolName": "ls", "error": false}
  ],
  "events": [
    {"type": "agent_started"},
    {"type": "tool_call_started"},
    {"type": "agent_completed"}
  ]
}
```

같은 history는 session root 아래 JSONL 파일로도 남는다.

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

외부 MCP tool은 built-in tool이 아니다. 애플리케이션의 MCP client 활성화,
`agent.mcp.allowed-tools` allowlist, request의 `allowMcp=true`가 모두 충족될 때만
모델에 노출되며, 실행 결과에는 기존 공통 output limit이 적용된다.

## Safety policy

기본 모드는 read-only다.

- `allowWrite=false`, `allowBash=false`, `allowMcp=false`가 safe default다.
- read-only tools는 기본 활성화된다.
- `write`, `edit`은 `allowWrite=true`일 때만 활성화된다.
- `bash`는 `allowBash=true`일 때만 활성화된다.
- MCP tool은 client 활성화, allowlist, `allowMcp=true`가 모두 필요하다.
- `application.yml`에도 `write-enabled-by-default=false`,
  `bash-enabled-by-default=false`를 둔다. 현재 실행 gate는 request의
  `allowWrite`, `allowBash` 값으로 결정된다.

MCP safety:

- Spring AI MCP client는 기본 비활성화
- allowlist에 없는 `serverName/toolName`은 discovery 단계에서 제외
- MCP tool 이름은 `mcp_<server>_<tool>` prefix로 built-in tool과 분리
- MCP tool도 `ToolExecutionService`와 `ToolPolicy`를 통과
- MCP request timeout과 기존 tool output limit 적용
- tool call과 결과를 기존 SSE event와 JSONL history에 기록
- MCP tool annotation은 권한 판단 근거로 사용하지 않음
- 원격 MCP server의 파일/명령 접근은 로컬 `SafePathResolver` 보호 범위 밖이므로,
  server 자체를 별도 sandbox와 workspace 제한으로 실행해야 함
- Spring AI MCP server는 기본 비활성화되고 tool capability만 사용
- 서버 기본 노출은 `ls`, `read`, `find`, `grep`이며 client에서 노출 목록을 바꿀 수 없음
- `write`/`edit`과 `bash`는 server allowlist와 별도 permission flag가 모두 필요
- downstream MCP client tool은 MCP server에서 다시 노출하지 않음
- 서버 호출도 기존 `ToolExecutionService`, workspace/secret guard, command policy,
  output limit을 통과
- MCP server 호출은 agent session이 아니므로 가짜 session, SSE event, JSONL history를 만들지 않음

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

M12에서 회귀 테스트로 고정한 safety 사례:

- nested secret path 차단: `config/.env.local`, `keys/id_rsa`,
  `src/main/resources/application-prod.yml`
- PowerShell recursive delete variant 차단: `Remove-Item -Recurse -Force .`,
  `Remove-Item -r -Force .`, `ri -r .`, `rd -Recurse .`, `del -r .`,
  `erase -r .`, `rm -r .`
- policy denied reason이 `tool_call_denied` SSE event와 JSONL session history에 남는지 확인

## 데모 시나리오

### 1. Read-only 분석

목표: agent가 `ls`, `read`, `find`, `grep`만 사용해 프로젝트를 설명하는지 본다.

```json
{
  "text": "이 프로젝트의 구조와 핵심 패키지를 설명해줘",
  "allowWrite": false,
  "allowBash": false,
  "allowMcp": false
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
  "allowBash": false,
  "allowMcp": false
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
  "allowBash": true,
  "allowMcp": false
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
  "allowBash": true,
  "allowMcp": false
}
```

확인할 것:

- `BashCommandPolicy`가 명령을 거부한다.
- tool result는 error로 반환된다.
- 거부 흐름이 SSE event와 JSONL history에 남는다.

### 5. Session history / replay 확인

목표: 실시간 SSE와 나중에 조회하는 JSONL history가 같은 실행 흐름을 설명하는지 본다.

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/sessions/$($session.sessionId)"
```

확인할 것:

- `messages`에는 user, tool result, assistant 기록이 실행 순서대로 남는다.
- `events`에는 SSE로 흘렀던 `agent_started`, tool event, assistant event,
  `agent_completed`가 남는다.
- `.pi-spring-ai/sessions/{sessionId}/messages.jsonl`,
  `.pi-spring-ai/sessions/{sessionId}/events.jsonl`을 직접 열어도 같은 흐름을 확인할 수 있다.
- 이 조회 API는 replay/debug용이며 branch/fork, compaction, DB 저장을 구현하지 않는다.

### 6. MCP tool 명시 활성화

목표: Spring AI가 발견한 외부 tool도 기존 policy와 기록 흐름을 통과하는지 본다.

```json
{
  "text": "MCP echo tool로 hello를 보내고 결과를 설명해줘",
  "allowWrite": false,
  "allowBash": false,
  "allowMcp": true
}
```

확인할 것:

- MCP client가 비활성화됐거나 allowlist에 없으면 tool이 모델에 노출되지 않는다.
- 허용된 tool은 `mcp_demo_echo`처럼 prefix된 이름으로 노출된다.
- 실행 시 `tool_call_started`, `tool_call_finished` SSE event가 발생한다.
- tool result가 `messages.jsonl`, event가 `events.jsonl`에 기록된다.

### 7. Built-in tool MCP server 노출

목표: 별도 LLM이나 원격 MCP server 없이 이 프로젝트의 read-only tool을 MCP client가
조회하고 호출할 수 있는지 확인한다.

```powershell
.\gradlew.bat test --tests "com.example.pispringai.mcp.McpServerIntegrationTest"
```

확인할 것:

- `/mcp`의 `tools/list`에는 `ls`, `read`, `find`, `grep`만 나온다.
- `read`로 workspace 안의 `README.md`를 읽을 수 있다.
- `read`로 `..`에 접근하면 기존 workspace safety가 차단한다.
- 테스트는 API key와 외부 MCP server 없이 통과한다.

## 다음 확장 방향

M0부터 M15까지 작은 Pi-inspired coding agent harness의 MVP와 post-MVP 본편을
구현했다. MCP client와 server는 모두 core runtime을 대체하지 않는 adapter로 붙였다.

| Milestone | 상태 | 방향 | 핵심 기준 |
|---|---|---|---|
| M12 | 완료 | Tool safety hardening | path/secret/bash deny 사례를 더 촘촘히 테스트하고, 기존 output limit 회귀 테스트를 유지 |
| M13 | 완료 | Session history/replay polish | SSE와 JSONL session history를 조회, 설명, 재현 가능한 흐름으로 정리 |
| M14 | 완료 | MCP client integration | 외부 MCP server의 tool을 기존 `ToolRegistry`/`ToolPolicy` 경계 안으로 opt-in 연결 |
| M15 | 완료 | MCP server exposure | built-in tools를 Spring AI MCP server로 노출하되 read-only와 이중 opt-in 원칙 유지 |

시즌 2 또는 옵션으로 미룰 항목:

- RAG/Vector DB: 코딩 agent runtime이 아니라 knowledge retrieval 주제이므로 별도 흐름으로 분리
- Tool Search Advisor: MCP와 tool registry가 안정화된 뒤 검토
- CLI, TUI, dashboard: runtime을 우회하지 않는 얇은 operator surface로만 검토
- LLM-as-Judge evaluation: 기본 테스트가 API key 없이 통과해야 하므로 opt-in profile/demo로만 검토
- session branch/fork, compaction: JSONL replay가 충분히 설명 가능해진 뒤 확장
- multi-agent, plugin/package system: MVP와 post-MVP 본편 범위 밖

확장은 MVP가 계속 테스트로 보호되는 상태에서 한 단계씩 붙인다.
