# 02. Design — pi-spring-ai 설계 문서

문서 버전: 0.1
기준 문서: `docs/01_SOT.md`
설계 목표: Spring/Spring AI에 자연스럽게 녹아드는 작은 Agent Runtime 설계

---

## 1. 전체 구조

MVP는 단일 Spring Boot 애플리케이션으로 시작한다.

```text
pi-spring-ai
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/java/com/example/pispringai
│   ├── PiSpringAiApplication.java
│   ├── agent
│   ├── api
│   ├── model
│   ├── session
│   ├── tool
│   ├── workspace
│   └── config
└── src/test/java/com/example/pispringai
```

패키지 역할:

| 패키지 | 역할 |
|---|---|
| `agent` | AgentRuntime, AgentEventPublisher, agent loop |
| `api` | REST/SSE Controller, request/response DTO |
| `model` | Spring AI ChatClient wrapper, model config |
| `session` | AgentSession, JSONL SessionStore |
| `tool` | AgentTool, built-in tools, ToolPolicy, ToolCallback adapter |
| `workspace` | workspace root, safe path resolver, file guard |
| `config` | Spring Bean configuration, properties |

---

## 2. 설계 원칙

### 2.1 단일 앱, 패키지 분리

처음부터 multi-module로 나누지 않는다. MVP에서는 한 프로젝트 안에서 패키지로만 구분한다.

나중에 다음 조건이 충족되면 모듈 분리를 검토한다.

- tool 패키지가 10개 이상으로 커진다.
- session storage를 여러 구현으로 운영한다.
- CLI와 server를 동시에 유지해야 한다.

### 2.2 Spring AI는 모델 호출 계층에 둔다

Agent 도메인이 Spring AI 타입에 과하게 의존하지 않도록 한다.

권장 방향:

```text
AgentRuntime
  → ModelClient
    → SpringAiModelClient
      → ChatClient
```

`AgentRuntime`은 `ChatClient`를 직접 많이 알지 않는다. 그래야 나중에 fake model, Ollama, 다른 provider 테스트가 쉬워진다.

### 2.3 Tool은 내부 모델과 Spring AI adapter를 분리한다

내부 도구는 다음처럼 단순하게 둔다.

```java
public interface AgentTool {
    ToolSpec spec();
    ToolResult execute(ToolArguments arguments, ToolExecutionContext context);
}
```

Spring AI에 넘길 때만 `ToolCallback` 또는 `@Tool` adapter로 감싼다.

이유:

- policy 적용 위치가 명확해진다.
- tool 실행 이벤트를 직접 발행할 수 있다.
- fake test가 쉬워진다.
- Spring AI API 변경의 영향을 줄인다.

---

## 3. 런타임 흐름

MVP 흐름:

```text
Client
  → POST /api/sessions/{id}/messages
  → AgentRuntime.prompt(sessionId, userText)
  → SessionStore append user message
  → ContextBuilder builds prompt context
  → ModelClient streams response through Spring AI
  → ToolCallbackAdapter executes tools when model requests tool calls
  → ToolPolicy validates each tool call
  → ToolExecutionService executes the actual tool
  → AgentEventPublisher emits events
  → SessionStore appends assistant/tool messages
  → SSE emits AgentEvent to client
```

### 3.1 이벤트 흐름 예시

```jsonl
{"type":"agent_started","sessionId":"s1"}
{"type":"user_message_appended","messageId":"m1"}
{"type":"tool_call_started","tool":"ls","callId":"tc1"}
{"type":"tool_call_finished","tool":"ls","callId":"tc1","error":false}
{"type":"assistant_delta","text":"이 프로젝트는"}
{"type":"assistant_delta","text":" Spring Boot 기반입니다."}
{"type":"assistant_completed","messageId":"m2"}
{"type":"agent_completed","sessionId":"s1"}
```

### 3.2 Tool Calling 흐름

```text
LLM: read 도구를 호출하고 싶다
  ↓
Spring AI ToolCallbackAdapter
  ↓
ToolPolicy.before(tool, args, context)
  ↓ allowed or denied
ToolExecutionService.execute(...)
  ↓
ToolPolicy.after(result)
  ↓
ToolResult 반환
  ↓
Spring AI가 ToolResult를 모델에게 다시 전달
  ↓
LLM 최종 응답 생성
```

---

## 4. 핵심 타입 설계

### 4.1 AgentSession

```java
public record AgentSession(
    String id,
    Path workspaceRoot,
    Instant createdAt,
    Instant updatedAt,
    String title
) {}
```

MVP에서는 `title`이 없어도 된다. 하지만 UI/README 데모를 위해 두는 것을 권장한다.

### 4.2 AgentMessage

Java 21 sealed interface를 사용한다.

```java
public sealed interface AgentMessage
        permits UserMessage, AssistantMessage, ToolResultMessage, SystemNoteMessage {
    String id();
    String sessionId();
    String parentId();
    Instant createdAt();
}
```

```java
public record UserMessage(
    String id,
    String sessionId,
    String parentId,
    Instant createdAt,
    String text
) implements AgentMessage {}

public record AssistantMessage(
    String id,
    String sessionId,
    String parentId,
    Instant createdAt,
    String text,
    AssistantStopReason stopReason
) implements AgentMessage {}

public record ToolResultMessage(
    String id,
    String sessionId,
    String parentId,
    Instant createdAt,
    String toolCallId,
    String toolName,
    String resultText,
    boolean error
) implements AgentMessage {}
```

`parentId`는 MVP에서 항상 직전 메시지 ID를 넣는다. 나중에 branch/fork를 구현할 때 확장한다.

### 4.3 AgentEvent

```java
public sealed interface AgentEvent permits
    AgentStartedEvent,
    AgentCompletedEvent,
    UserMessageAppendedEvent,
    AssistantDeltaEvent,
    AssistantCompletedEvent,
    ToolCallStartedEvent,
    ToolCallFinishedEvent,
    ToolCallDeniedEvent,
    AgentErrorEvent {

    String eventId();
    String sessionId();
    Instant createdAt();
    AgentEventType type();
}
```

이벤트는 외부 관찰용이다. 메시지는 세션 기록용이다. 둘을 섞지 않는다.

### 4.4 ToolSpec

```java
public record ToolSpec(
    String name,
    String description,
    JsonSchema inputSchema,
    ToolSafetyLevel safetyLevel,
    boolean enabledByDefault
) {}
```

초기에는 `JsonSchema`를 직접 복잡하게 만들지 않아도 된다. MVP에서는 `Map<String, Object>` 또는 `String schemaJson`으로 시작해도 된다.

### 4.5 ToolArguments

```java
public record ToolArguments(Map<String, Object> values) {
    public String requiredString(String name) { ... }
    public Optional<String> optionalString(String name) { ... }
}
```

처음부터 generic type-safe mapper를 만들지 않는다. 실제 반복이 생긴 뒤 개선한다.

### 4.6 ToolResult

```java
public record ToolResult(
    String text,
    boolean error,
    Map<String, Object> metadata
) {
    public static ToolResult ok(String text) { ... }
    public static ToolResult error(String text) { ... }
}
```

---

## 5. AgentRuntime 설계

### 5.1 Public API

```java
public interface AgentRuntime {
    Flux<AgentEvent> prompt(AgentPromptCommand command);
}
```

```java
public record AgentPromptCommand(
    String sessionId,
    String userText,
    Path workspaceRoot,
    AgentRunOptions options
) {}
```

```java
public record AgentRunOptions(
    boolean allowWrite,
    boolean allowBash,
    int maxTurns,
    int maxToolCalls,
    Duration timeout
) {
    public static AgentRunOptions safeDefaults() { ... }
}
```

기본값:

```text
allowWrite = false
allowBash = false
maxTurns = 6
maxToolCalls = 12
timeout = 120 seconds
```

### 5.2 구현 클래스

```java
@Service
public class DefaultAgentRuntime implements AgentRuntime {
    private final SessionStore sessionStore;
    private final ModelClient modelClient;
    private final ToolRegistry toolRegistry;
    private final AgentEventSinkFactory eventSinkFactory;

    @Override
    public Flux<AgentEvent> prompt(AgentPromptCommand command) {
        // 1. create sink
        // 2. append user message
        // 3. call model
        // 4. emit events
        // 5. append assistant/tool messages
    }
}
```

주의:

- 첫 구현에서 완전한 manual loop를 만들지 않아도 된다.
- Spring AI `ChatClient`의 tool calling lifecycle을 활용한다.
- 단, tool execution event를 발행하기 위해 `ToolCallbackAdapter`에서 내부 tool executor를 호출한다.

---

## 6. ModelClient 설계

### 6.1 인터페이스

```java
public interface ModelClient {
    Flux<ModelEvent> stream(ModelPrompt prompt, ModelExecutionContext context);
}
```

MVP에서 복잡하면 다음처럼 더 단순하게 시작한다.

```java
public interface ModelClient {
    Flux<String> streamText(ModelPrompt prompt, ModelExecutionContext context);
}
```

하지만 tool event를 함께 다뤄야 하므로 최종적으로는 `ModelEvent`가 낫다.

### 6.2 SpringAiModelClient

```java
@Component
public class SpringAiModelClient implements ModelClient {
    private final ChatClient chatClient;
    private final ToolCallbackProvider toolCallbackProvider;

    @Override
    public Flux<ModelEvent> stream(ModelPrompt prompt, ModelExecutionContext context) {
        // ChatClient prompt 구성
        // system/user/history 구성
        // tools 연결
        // stream response를 ModelEvent로 변환
    }
}
```

구현 시 주의:

- Spring AI 버전에 따라 streaming response 타입이 다를 수 있으니 실제 API를 확인하고 맞춘다.
- 처음에는 text streaming만 먼저 붙이고, tool events는 tool adapter에서 emit해도 된다.

---

## 7. Tool 설계

### 7.1 ToolRegistry

```java
@Component
public class ToolRegistry {
    private final Map<String, AgentTool> tools;

    public List<AgentTool> enabledTools(AgentRunOptions options) { ... }
    public AgentTool get(String name) { ... }
}
```

활성화 규칙:

```text
read-only tools: 항상 활성화
write/edit: options.allowWrite == true 일 때 활성화
bash: options.allowBash == true 일 때 활성화
```

### 7.2 ToolExecutionService

```java
@Service
public class ToolExecutionService {
    private final ToolPolicy toolPolicy;

    public ToolResult execute(AgentTool tool, ToolArguments args, ToolExecutionContext context) {
        ToolPolicyDecision decision = toolPolicy.before(tool, args, context);
        if (decision.denied()) {
            return ToolResult.error(decision.reason());
        }

        ToolResult result = tool.execute(args, context);
        return toolPolicy.after(tool, args, result, context);
    }
}
```

### 7.3 ToolPolicy

```java
public interface ToolPolicy {
    ToolPolicyDecision before(AgentTool tool, ToolArguments args, ToolExecutionContext context);
    ToolResult after(AgentTool tool, ToolArguments args, ToolResult result, ToolExecutionContext context);
}
```

MVP 정책:

- workspace 내부 경로만 허용
- secret 파일 deny
- 파일 크기 제한
- 출력 길이 제한
- write/edit는 명시적 allow 필요
- bash는 명시적 allow 필요
- bash는 denylist/allowlist 검사

### 7.4 Built-in tools

#### LsTool

입력:

```json
{"path":"."}
```

동작:

- workspace root 기준 path resolve
- 디렉터리 목록 반환
- 최대 항목 수 제한

#### ReadTool

입력:

```json
{"path":"README.md"}
```

동작:

- 텍스트 파일만 읽기
- 최대 byte 수 제한
- secret 파일 차단

#### FindTool

입력:

```json
{"pattern":"*.java"}
```

동작:

- workspace 내부 파일명 검색
- `.git`, `build`, `.gradle` 기본 제외

#### GrepTool

입력:

```json
{"query":"AgentRuntime", "path":"src"}
```

동작:

- 텍스트 파일만 검색
- 최대 match 수 제한

#### WriteTool

입력:

```json
{"path":"docs/demo.md", "content":"..."}
```

동작:

- `allowWrite`가 true일 때만 실행
- 기본적으로 기존 파일 overwrite 금지
- overwrite 옵션은 명시적으로 받아야 함

#### EditTool

입력:

```json
{"path":"src/main/.../Foo.java", "oldText":"...", "newText":"..."}
```

동작:

- `oldText`가 정확히 한 번만 매칭될 때 수정
- 0회 또는 2회 이상이면 실패
- patch 기반 도구는 후속 개선

#### BashTool

입력:

```json
{"command":"./gradlew test"}
```

동작:

- `allowBash`가 true일 때만 실행
- timeout 적용
- 출력 제한
- destructive command deny
- command allowlist는 후속 개선 가능

MVP에서 bash는 옵션 기능이다. read-only agent가 먼저 완성되어야 한다.

---

## 8. Workspace 설계

### 8.1 WorkspaceProperties

```java
@ConfigurationProperties(prefix = "agent.workspace")
public record WorkspaceProperties(
    Path root,
    long maxReadBytes,
    int maxDirectoryEntries,
    int maxGrepMatches
) {}
```

기본값:

```yaml
agent:
  workspace:
    root: .
    max-read-bytes: 200000
    max-directory-entries: 200
    max-grep-matches: 100
```

### 8.2 SafePathResolver

```java
@Component
public class SafePathResolver {
    public Path resolveInsideWorkspace(Path workspaceRoot, String userPath) {
        Path root = workspaceRoot.toRealPath().normalize();
        Path resolved = root.resolve(userPath).normalize();
        if (!resolved.startsWith(root)) {
            throw new WorkspaceAccessDeniedException(...);
        }
        return resolved;
    }
}
```

주의:

- symlink escape를 고려해 `toRealPath()` 사용을 검토한다.
- 존재하지 않는 파일을 write할 때는 parent directory를 realpath로 검사한다.

### 8.3 SecretFileGuard

```java
@Component
public class SecretFileGuard {
    public boolean isDenied(Path path) { ... }
}
```

기본 deny pattern:

```text
.env
.env.*
*.pem
*.key
id_rsa
id_ed25519
secrets.*
application-prod.yml
application-prod.yaml
```

---

## 9. SessionStore 설계

### 9.1 인터페이스

```java
public interface SessionStore {
    AgentSession create(Path workspaceRoot);
    Optional<AgentSession> findSession(String sessionId);
    void appendMessage(String sessionId, AgentMessage message);
    void appendEvent(String sessionId, AgentEvent event);
    List<AgentMessage> loadMessages(String sessionId);
    List<AgentEvent> loadEvents(String sessionId);
}
```

### 9.2 파일 구조

```text
.pi-spring-ai/
  sessions/
    {sessionId}/
      session.json
      messages.jsonl
      events.jsonl
```

### 9.3 JSONL record 예시

`messages.jsonl`

```json
{"kind":"user","id":"m1","sessionId":"s1","parentId":null,"createdAt":"...","text":"프로젝트 분석해줘"}
{"kind":"tool_result","id":"m2","sessionId":"s1","parentId":"m1","createdAt":"...","toolCallId":"tc1","toolName":"ls","resultText":"README.md","error":false}
```

`events.jsonl`

```json
{"type":"tool_call_started","eventId":"e1","sessionId":"s1","createdAt":"...","tool":"ls","callId":"tc1"}
{"type":"tool_call_finished","eventId":"e2","sessionId":"s1","createdAt":"...","tool":"ls","callId":"tc1","error":false}
```

### 9.4 ObjectMapper 설정

Java time, sealed type 직렬화를 고려한다.

M3에서는 sealed interface에 Jackson 다형성 설정을 직접 붙이지 않고 envelope DTO를 사용한다.

이유:

- JSONL은 한 줄이 독립적인 JSON record여야 하므로 `kind`/`type` discriminator가 사람이 바로 읽혀야 한다.
- `AgentMessage`, `AgentEvent` 도메인 타입을 Jackson 저장 방식에 과하게 묶지 않는다.
- subtype annotation을 도메인 계층에 늘리기보다 session 저장 계층에서 명시적으로 변환한다.
- M3 범위에서는 message/event append-load만 필요하고, API/SSE/runtime 역직렬화는 아직 필요하지 않다.

```java
public record StoredMessage(
    String kind,
    JsonNode json
) {}

public record StoredEvent(
    String type,
    JsonNode json
) {}
```

`StoredMessage`와 `StoredEvent`는 on-disk wrapper 형식이 아니라 load 과정에서 root JSON object를 들고 다니는 내부 DTO다.

즉 파일에 다음처럼 쓰지 않는다.

```json
{"kind":"user","payload":{"id":"m1", "...":"..."}}
```

파일에는 M2에서 고정한 flat concrete JSON object를 그대로 쓴다.

```json
{"kind":"user","id":"m1","sessionId":"s1","parentId":null,"createdAt":"...","text":"프로젝트 분석해줘"}
```

`JsonlSessionStore`는 저장 시 concrete record를 flat JSON object로 쓰고, 읽을 때는 root object의 `kind` 또는 `type` 값으로 switch 하여 concrete record로 복원한다.

M3에서 명시적으로 테스트한다:

- flat JSONL shape이며 `payload` wrapper가 생기지 않는다.
- append 순서대로 load된다.
- 알 수 없는 `kind`/`type`은 실패한다.
- `kind`/`type` 누락 또는 문자열이 아닌 discriminator는 실패한다.
- malformed JSONL은 실패한다.
- `findSession`은 없는 session에 대해 empty를 반환한다.
- `appendMessage`, `appendEvent`, `loadMessages`, `loadEvents`는 없는 session에 대해 명확히 실패한다.
- sessionId로 session root 밖 경로를 만들 수 없다.
- 손상된 `session.json`은 조용히 empty로 처리하지 않고 실패한다.

M3에서 하지 않는다:

- `@JsonTypeInfo` 기반 sealed hierarchy 전역 다형성 설정
- 별도 DB 또는 index 파일
- session branch/fork
- compaction
- AgentRuntime/API/SSE 연결

MVP에서는 유지보수 쉬운 방법을 우선한다.

---

## 10. API 설계

### 10.1 세션 생성

```http
POST /api/sessions
Content-Type: application/json

{
  "workspaceRoot": "/path/to/project"
}
```

응답:

```json
{
  "sessionId": "s_...",
  "workspaceRoot": "/path/to/project"
}
```

### 10.2 메시지 전송 + 이벤트 스트림

단순화를 위해 MVP에서는 하나의 endpoint에서 SSE를 반환한다.

```http
POST /api/sessions/{sessionId}/messages/stream
Content-Type: application/json
Accept: text/event-stream

{
  "text": "이 프로젝트 구조를 분석해줘",
  "allowWrite": false,
  "allowBash": false
}
```

응답:

```text
event: agent_event
data: {"type":"agent_started",...}

event: agent_event
data: {"type":"tool_call_started",...}

event: agent_event
data: {"type":"assistant_delta","text":"..."}
```

### 10.3 세션 조회

```http
GET /api/sessions/{sessionId}
```

응답:

```json
{
  "session": {...},
  "messages": [...],
  "events": [...]
}
```

---

## 11. 설정 설계

`application.yml`

```yaml
spring:
  application:
    name: pi-spring-ai
  ai:
    model:
      chat: ${AGENT_MODEL_PROVIDER:google-genai}
    google:
      genai:
        api-key: ${GEMINI_API_KEY:${GOOGLE_API_KEY:}}
        chat:
          model: ${GEMINI_MODEL:gemini-2.5-flash}
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat:
        model: ${OPENAI_MODEL:gpt-4.1-mini}

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
  model:
    provider: ${AGENT_MODEL_PROVIDER:google-genai}
    name: ${AGENT_MODEL_NAME:${GEMINI_MODEL:gemini-2.5-flash}}
```

실제 모델명은 구현 시점의 사용 가능 모델로 조정한다. SOT의 핵심은 특정 모델명이 아니라 모델 호출 계층 분리다.

---

## 12. 테스트 설계

### 12.1 Unit tests

필수:

- `SafePathResolverTest`
- `SecretFileGuardTest`
- `ReadToolTest`
- `LsToolTest`
- `GrepToolTest`
- `JsonlSessionStoreTest`
- `ToolPolicyTest`

### 12.2 Integration tests

필수:

- `AgentApiSmokeTest`
- `AgentRuntimeReadOnlyTest`

외부 LLM API를 호출하지 않는다. fake model 또는 mock을 사용한다.

### 12.3 Test fixture

```text
src/test/resources/fixtures/sample-project/
  README.md
  build.gradle.kts
  src/main/java/demo/App.java
  .env
```

검증:

- README는 읽을 수 있다.
- `.env`는 읽을 수 없다.
- `../outside.txt`는 읽을 수 없다.

---

## 13. 에러 처리

에러는 숨기지 말고 이벤트로 남긴다.

```java
public record AgentErrorEvent(
    String eventId,
    String sessionId,
    Instant createdAt,
    String message,
    String errorCode,
    boolean recoverable
) implements AgentEvent {}
```

도구 실패는 전체 agent 실패가 아닐 수 있다. 예를 들어 `read`가 denied되면 tool result로 error를 반환하고 모델이 사용자에게 설명하게 한다.

---

## 14. 과하지 않게 유지하기 위한 설계 컷라인

다음이 보이면 멈추고 단순화한다.

- 인터페이스가 구현체보다 많다.
- 아직 사용하지 않는 추상화가 있다.
- 테스트가 어려운 private helper가 많다.
- tool 하나 추가하는 데 5개 파일 이상 수정해야 한다.
- 세션 저장을 이해하려면 Jackson 고급 설정을 많이 알아야 한다.
- API가 데모보다 먼저 복잡해진다.

권장 상태:

- tool 하나는 클래스 하나 + 테스트 하나로 추가 가능
- policy는 하나의 서비스에서 눈으로 읽힌다
- session JSONL은 사람이 열어봐도 이해 가능
- AgentRuntime은 200~300줄을 넘기기 전까지 분리하지 않는다

---

## 15. 후속 확장 설계

MVP 이후 순서:

1. `write`, `edit` 안정화
2. `bash` command policy 강화
3. session branch/fork
4. compaction
5. Tool Search Advisor
6. MCP client
7. MCP server
8. CLI
9. Observability dashboard
10. LLM-as-Judge evaluation

확장은 반드시 MVP가 동작하고 테스트가 생긴 뒤 진행한다.
