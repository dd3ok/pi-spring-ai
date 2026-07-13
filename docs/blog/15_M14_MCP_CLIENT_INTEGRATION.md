# M14. MCP를 붙여도 기존 tool 경계는 바뀌지 않는다

> 이 글은 `Spring AI로 Pi 스타일 코딩 에이전트 만들기` 시리즈의 일부입니다.
> 목표는 거대한 프레임워크를 한 번에 만드는 것이 아니라, Spring 개발자가 이해할 수 있는 작은 단위로 에이전트 런타임을 쌓아가는 데 있습니다.

## 이번 글의 목적

M13에서는 SSE와 JSONL session history를 한 흐름으로 정리했습니다.

SSE는 지금 실행 중인 agent를 관찰하는 통로이고, JSONL은 나중에 같은 실행을 다시 확인하는 기록입니다. 글의 마지막에는 다음 단계의 방향도 남겼습니다.

```text
MCP를 붙이더라도 기존 ToolRegistry, ToolPolicy, SSE, JSONL session history를 우회하지 않는다.
```

이번 M14는 그 약속을 구현하는 단계입니다.

MCP server가 제공하는 tool을 Spring AI MCP client로 발견하되, 발견한 callback을 `ChatClient`에 곧바로 넘기지 않습니다. 먼저 프로젝트의 `AgentTool`로 감싼 뒤 기존 실행 경계 안으로 넣습니다.

이번 글에서 답할 질문은 네 가지입니다.

```text
Spring AI의 MCP client를 어디까지 맡길 것인가?
외부 tool을 어떻게 기존 ToolRegistry와 ToolPolicy 안으로 넣을 것인가?
MCP tool은 어떤 조건에서 모델에 노출할 것인가?
외부 서버를 호출하면서도 기존 SSE와 JSONL 기록을 어떻게 유지할 것인가?
```

## M14에서 만들지 않는 것

먼저 범위를 줄입니다.

MCP에는 tool 외에도 resource, prompt, sampling 같은 기능이 있습니다. 인증과 전송 방식도 더 넓게 다룰 수 있습니다. 하지만 M14에서는 외부 tool을 기존 runtime에 연결하는 한 가지 흐름만 구현합니다.

```text
MCP server를 만들지 않는다.
MCP resource와 prompt를 연결하지 않는다.
sampling을 구현하지 않는다.
async MCP client를 추가하지 않는다.
OAuth를 구현하지 않는다.
AgentRuntime loop를 바꾸지 않는다.
MCP 전용 event나 session 저장소를 만들지 않는다.
```

MCP 자체를 다시 구현하는 일도 하지 않습니다.

transport, client lifecycle, initialize, tool discovery는 Spring AI에 맡깁니다. 프로젝트가 직접 책임질 부분은 외부 tool을 어떤 이름으로 받아들이고, 언제 노출하며, 어느 정책 경계를 통과시킬지입니다.

## 왜 Spring AI MCP client를 사용하는가

MCP client를 직접 만들 수도 있습니다.

하지만 그러면 HTTP transport, protocol handshake, tool 목록 조회, callback 변환, timeout을 모두 애플리케이션 코드에서 관리해야 합니다. 이 프로젝트의 목적은 MCP SDK를 새로 만드는 것이 아니라 coding agent runtime의 경계를 설명하는 데 있습니다.

그래서 WebFlux 애플리케이션과 맞는 starter 하나를 추가했습니다.

```kotlin
implementation("org.springframework.ai:spring-ai-starter-mcp-client-webflux")
```

버전은 직접 적지 않습니다.

프로젝트가 이미 사용 중인 Spring AI BOM `2.0.0`이 starter와 MCP SDK 버전을 함께 관리합니다. 이렇게 해야 Spring AI와 하위 MCP SDK 사이의 조합을 애플리케이션이 따로 맞추지 않아도 됩니다.

Spring AI에는 이번 단계에 필요한 확장 지점도 이미 있습니다.

```text
SyncMcpToolCallbackProvider
McpToolFilter
McpToolNamePrefixGenerator
ToolCallbackProvider
```

M14에서는 이 API를 사용합니다. 별도 MCP client abstraction이나 provider hierarchy를 만들지 않습니다.

## 전체 실행 흐름

M14 이후의 tool 호출 흐름은 다음과 같습니다.

```text
Spring AI SyncMcpToolCallbackProvider
  -> McpToolFilter
  -> McpToolNamePrefixGenerator
  -> McpToolBridge
  -> McpAgentTool
  -> ToolRegistry
  -> SpringAiToolCallbackAdapter
  -> ToolExecutionService
  -> ToolPolicy
  -> MCP server
  -> SSE event
  -> JSONL session history
```

여기서 중요한 부분은 가운데입니다.

Spring AI가 만든 `ToolCallback`을 그대로 모델에 등록하면 호출은 되지만, 이 프로젝트가 지금까지 만든 정책 경계를 지나지 않습니다. `allowMcp`, 공통 output limit, tool event, session history가 빠질 수 있습니다.

그래서 `McpToolBridge`가 callback을 `McpAgentTool`로 바꿉니다.

```java
public List<McpAgentTool> tools() {
    SyncMcpToolCallbackProvider provider = callbackProvider.getIfAvailable();
    if (provider == null) {
        return List.of();
    }
    return Arrays.stream(provider.getToolCallbacks())
            .map(callback -> new McpAgentTool(callback, objectMapper))
            .toList();
}
```

MCP client가 비활성화되어 provider bean이 없으면 빈 목록을 돌려줍니다. 기존 built-in tool은 그대로 동작합니다.

## 세 단계 opt-in

MCP tool은 세 조건을 모두 만족해야 모델에 보입니다.

```text
1. 애플리케이션에서 MCP client를 활성화한다.
2. serverName/toolName을 allowlist에 넣는다.
3. 요청에서 allowMcp=true를 보낸다.
```

한 단계만 켜서는 부족합니다.

첫 번째 조건은 운영자가 외부 연결 자체를 허용했다는 뜻입니다.

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: ${AGENT_MCP_ENABLED:false}
        type: SYNC
        request-timeout: ${AGENT_MCP_REQUEST_TIMEOUT:20s}
```

두 번째 조건은 연결된 서버의 모든 tool을 자동으로 신뢰하지 않겠다는 뜻입니다.

```yaml
agent:
  mcp:
    allowed-tools: ${AGENT_MCP_ALLOWED_TOOLS:}
```

세 번째 조건은 이번 agent run에서 외부 tool 사용을 명시적으로 허용한다는 뜻입니다.

```json
{
  "text": "MCP echo tool로 hello를 보내줘",
  "allowWrite": false,
  "allowBash": false,
  "allowMcp": true
}
```

`allowMcp`를 생략하면 `false`입니다.

```java
public static AgentRunOptions safeDefaults() {
    return new AgentRunOptions(
            false,
            false,
            false,
            6,
            12,
            Duration.ofSeconds(120));
}
```

`allowWrite`, `allowBash`와 같은 방식입니다. 외부 tool도 요청 단위 권한으로 다룹니다.

## allowlist는 server와 tool의 쌍이다

allowlist에는 `serverName/toolName` 형식을 사용합니다.

예를 들어 initialize 응답에 담긴 server name이 `demo`이고 tool name이 `echo`라면 다음과 같습니다.

```text
demo/echo
```

필터는 정확히 일치하는 항목만 허용합니다.

```java
@Override
public boolean test(McpConnectionInfo connectionInfo, McpSchema.Tool tool) {
    if (connectionInfo == null || connectionInfo.initializeResult() == null || tool == null) {
        return false;
    }
    McpSchema.Implementation serverInfo = connectionInfo.initializeResult().serverInfo();
    return serverInfo != null && properties.allows(serverInfo.name(), tool.name());
}
```

초기화 정보가 없거나 server name을 확인할 수 없으면 허용하지 않습니다.

여기서 connection 설정 이름과 server name을 구분해야 합니다. allowlist가 보는 값은 YAML의 connection key가 아니라, MCP initialize 응답의 `serverInfo.name`입니다.

이 기준을 택한 이유는 실제 tool 제공자를 기준으로 권한을 고정하기 위해서입니다. 설정 이름이 같더라도 다른 identity를 가진 server가 연결됐다면 자동으로 허용해서는 안 됩니다.

## 이름 충돌을 막는다

built-in tool에는 이미 `read`, `grep`, `bash` 같은 짧은 이름이 있습니다.

외부 MCP server도 같은 이름의 tool을 제공할 수 있습니다. 그대로 합치면 모델이 어느 tool을 호출하는지 구분하기 어렵고, 기존 tool을 덮어쓸 수도 있습니다.

그래서 MCP tool에는 prefix를 붙입니다.

```text
server name: demo
tool name: echo
model에 노출되는 이름: mcp_demo_echo
```

구현은 Spring AI의 `McpToolNamePrefixGenerator`를 사용합니다.

```java
String serverName = McpToolUtils.format(serverInfo.name());
String toolName = McpToolUtils.format(tool.name());
String formatted = "mcp_" + serverName + "_" + toolName;
```

모델에 전달할 이름은 지원되는 문자로 정리하고 64자를 넘지 않게 제한합니다.

이름이 겹치면 조용히 하나를 선택하지 않습니다. `ToolRegistry`가 중복을 발견하고 실패시킵니다.

```java
if (indexed.putIfAbsent(name, tool) != null) {
    throw new IllegalArgumentException("duplicate tool name: " + name);
}
```

외부 tool이 built-in tool을 덮어쓰는 것보다 요청을 실패시키는 편이 안전합니다.

## MCP callback을 AgentTool로 감싼다

`McpAgentTool`은 Spring AI `ToolCallback`과 프로젝트의 `AgentTool` 사이를 잇는 작은 adapter입니다.

```java
public final class McpAgentTool implements AgentTool {

    private final ToolCallback delegate;
    private final ToolSpec spec;
    private final ToolDefinition toolDefinition;

    @Override
    public ToolResult execute(ToolArguments arguments) {
        String input = objectMapper.writeValueAsString(arguments.values());
        String output = delegate.call(input);
        return ToolResult.ok(output, Map.of("source", "mcp"));
    }
}
```

안전 등급은 별도의 `MCP`로 둡니다.

```java
this.spec = new ToolSpec(
        toolDefinition.name(),
        description,
        Map.of(),
        ToolSafetyLevel.MCP,
        false);
```

`enabledByDefault`는 `false`입니다.

이 adapter가 생기면서 외부 tool도 `ToolExecutionService`로 실행할 수 있습니다. 실행 전에는 `ToolPolicy.before(...)`가 `allowMcp`를 검사하고, 실행 뒤에는 `ToolPolicy.after(...)`가 공통 output limit을 적용합니다.

```java
case MCP -> context.options().allowMcp()
        ? ToolPolicyDecision.allow()
        : ToolPolicyDecision.deny(
                "tool requires allowMcp=true: " + tool.spec().name());
```

MCP callback을 직접 호출하는 예외 경로는 만들지 않았습니다.

## 원본 ToolDefinition을 보존한다

MCP tool의 입력 schema는 built-in tool보다 복잡할 수 있습니다.

중첩 object, array, `required` 같은 JSON Schema 정보가 들어올 수 있습니다. 이것을 기존 `ToolSpec`의 단순한 map으로 바꿨다가 다시 만들면 정보가 사라질 수 있습니다.

그래서 `McpAgentTool`은 원본 `ToolDefinition`을 보관합니다.

`SpringAiToolCallbackAdapter`도 외부 definition이 들어오면 새 schema를 만들지 않고 그대로 사용합니다.

```java
private ToolDefinition resolveToolDefinition(ToolDefinition providedDefinition) {
    if (providedDefinition == null) {
        return toToolDefinition(spec);
    }
    if (!spec.name().equals(providedDefinition.name())) {
        throw new IllegalArgumentException(
                "tool definition name must match tool spec: " + spec.name());
    }
    return providedDefinition;
}
```

기존 built-in tool은 지금까지의 schema 변환 방식을 유지합니다. MCP tool만 원본 definition을 전달합니다.

이렇게 하면 adapter의 역할이 분명해집니다.

```text
ToolSpec은 내부 policy와 registry가 볼 최소 정보다.
ToolDefinition은 모델에 전달할 정확한 JSON Schema다.
```

## request 시점에 tool을 조회한다

MCP server의 tool 목록은 애플리케이션 실행 중 바뀔 수 있습니다.

한 번 조회한 callback 배열을 `SpringAiModelClient` 생성 시점에 고정하면 Spring AI의 tool change 처리와 어긋날 수 있습니다. 그래서 model request에는 callback 배열 대신 `ToolCallbackProvider`를 넘깁니다.

```java
return chatClient.prompt()
        .messages(toSpringMessages(command.messages()))
        .tools(toolCallbackFactory.providerFor(
                command.options(),
                command.workspaceRoot(),
                events))
        .stream()
        .content();
```

provider가 tool callback을 요청받는 시점에 MCP tool을 다시 해석합니다.

```java
public ToolCallbackProvider providerFor(
        AgentRunOptions options,
        Path workspaceRoot,
        SpringAiToolCallbackEvents callbackEvents
) {
    return () -> callbacksFor(options, workspaceRoot, callbackEvents)
            .toArray(ToolCallback[]::new);
}
```

새 cache나 refresh scheduler를 만들지 않았습니다. Spring AI가 이미 제공하는 provider 경계를 사용했습니다.

## SSE와 JSONL은 그대로 유지된다

MCP tool도 최종적으로 `SpringAiToolCallbackAdapter`를 통과합니다.

따라서 M8부터 사용하던 event가 그대로 발생합니다.

```text
tool_call_started
tool_call_finished
tool_call_denied
tool_call_failed
```

tool result도 같은 `ToolResultMessage`로 저장됩니다.

```text
.pi-spring-ai/sessions/{sessionId}/
  messages.jsonl
  events.jsonl
```

MCP 전용 event type이나 별도 audit 파일은 추가하지 않았습니다.

이 선택은 M13과도 이어집니다. tool의 출처가 built-in인지 MCP인지는 달라도, 사용자가 실행 흐름을 확인하는 방법은 같아야 합니다. SSE로 지금 상태를 보고, session history API나 JSONL 파일로 나중에 다시 확인합니다.

## MCP server는 로컬 workspace 경계 밖에 있다

여기서 가장 중요한 제한을 짚어야 합니다.

이 프로젝트의 `SafePathResolver`, `SecretFileGuard`, `BashCommandPolicy`는 로컬 built-in tool을 보호합니다. 원격 MCP server 내부에서 일어나는 파일 접근이나 명령 실행까지 통제하지는 못합니다.

```text
pi-spring-ai의 workspace safety
  -> 로컬 ls/read/find/grep/write/edit/bash에 적용

원격 MCP server의 파일·명령 접근
  -> 해당 server의 sandbox와 policy가 책임짐
```

따라서 `allowMcp=true`는 외부 tool을 무조건 안전하다고 선언하는 옵션이 아닙니다.

운영자는 신뢰할 수 있는 server만 연결하고, tool을 allowlist로 좁혀야 합니다. 파일이나 shell을 다루는 MCP server라면 그 server도 별도 workspace와 sandbox 안에서 실행해야 합니다.

MCP tool annotation도 권한 판단 근거로 사용하지 않습니다. server가 제공하는 메타데이터는 설명에 참고할 수 있지만, 실행 권한은 로컬 설정과 request option으로 결정합니다.

## 데모 설정

Streamable HTTP MCP server가 `http://localhost:3001/mcp`에서 실행 중이라고 가정하겠습니다.

initialize 응답의 server name은 `demo`, 사용할 tool name은 `echo`입니다.

PowerShell에서는 다음처럼 실행합니다.

```powershell
$env:AGENT_MCP_ENABLED = "true"
$env:AGENT_MCP_ALLOWED_TOOLS = "demo/echo"
.\gradlew.bat bootRun --args="--spring.ai.mcp.client.streamable-http.connections.demo.url=http://localhost:3001"
```

Spring AI WebFlux MCP client는 기본 endpoint인 `/mcp`로 연결합니다.

session을 만든 뒤 message stream API를 호출합니다.

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
  -Body '{"text":"MCP echo tool로 hello를 보내고 결과를 설명해줘","allowWrite":false,"allowBash":false,"allowMcp":true}'
```

확인할 항목은 다음과 같습니다.

```text
허용된 tool이 mcp_demo_echo 이름으로 모델에 노출되는가?
tool_call_started와 tool_call_finished가 SSE로 보이는가?
tool result가 messages.jsonl에 남는가?
event가 events.jsonl에 남는가?
allowMcp=false로 바꾸면 MCP tool이 노출되지 않는가?
allowlist에서 demo/echo를 빼면 discovery 단계에서 제외되는가?
```

실제 server name은 connection key가 아니라 initialize 응답에서 확인해야 합니다.

## 테스트로 고정한 것

M14 테스트는 외부 MCP server를 호출하지 않습니다.

먼저 allowlist와 name prefix를 작은 단위로 확인합니다.

```java
assertThat(filter.test(connection("demo"), tool("echo"))).isTrue();
assertThat(filter.test(connection("other"), tool("echo"))).isFalse();
assertThat(filter.test(connection("demo"), tool("read"))).isFalse();
```

Spring AI의 실제 `SyncMcpToolCallbackProvider`도 사용합니다. 다만 그 아래의 `McpSyncClient`는 mock으로 둡니다.

이 테스트에서 확인하는 것은 transport가 아니라 adapter 계약입니다.

```text
Spring AI provider가 tool을 발견하는가?
filter와 prefix가 적용되는가?
McpAgentTool이 원래 remote tool name과 argument로 callback을 호출하는가?
```

그다음 runtime 통합 테스트에서는 fake MCP callback을 기존 흐름에 넣습니다.

```text
McpAgentTool
  -> SpringAiToolCallbackFactory
  -> ToolExecutionService
  -> DefaultToolPolicy
  -> DefaultAgentRuntime
  -> JsonlSessionStore
```

결과 event 순서도 확인합니다.

```text
agent_started
user_message_appended
tool_call_started
tool_call_finished
assistant_delta
assistant_completed
agent_completed
```

마지막으로 MCP client가 기본 비활성화된 Spring context에서는 `SyncMcpToolCallbackProvider` bean이 생기지 않는지 확인합니다.

테스트의 원칙은 이전 단계와 같습니다.

```text
외부 LLM API를 호출하지 않는다.
원격 MCP server를 호출하지 않는다.
API key가 없어도 전체 테스트가 통과한다.
```

## 자체 리뷰에서 확인한 경계

구현을 마친 뒤에는 기능보다 우회 경로를 먼저 찾았습니다.

```text
MCP provider를 ChatClient에 직접 넘기는 코드가 있는가?
allowMcp=false인데 provider를 조회하는가?
allowlist에 없는 tool이 registry에 들어오는가?
MCP tool이 ToolExecutionService를 거치지 않는가?
원본 JSON Schema가 단순 schema로 바뀌는가?
MCP client가 기본 설정에서 외부 연결을 시도하는가?
```

현재 구조에서는 MCP callback이 `McpAgentTool`과 기존 adapter를 거쳐야 실행됩니다. `allowMcp=false`인 요청에서는 bridge 자체를 조회하지 않습니다. 이름 충돌은 registry에서 실패하고, callback의 원본 `ToolDefinition`도 보존합니다.

다만 실제 원격 server와의 수동 연결은 별도 실행 환경이 있어야 확인할 수 있습니다. 자동 테스트는 Spring AI provider와 mock client로 protocol 경계를 검증합니다.

## 테스트 흐름

먼저 MCP 관련 테스트만 실행합니다.

```text
./gradlew test --tests "com.example.pispringai.mcp.*"
```

model adapter와 policy 회귀 테스트도 함께 확인합니다.

```text
./gradlew test \
  --tests com.example.pispringai.model.SpringAiModelClientTest \
  --tests com.example.pispringai.model.SpringAiToolCallbackAdapterTest \
  --tests com.example.pispringai.tool.ToolRegistryTest \
  --tests com.example.pispringai.tool.policy.DefaultToolPolicyTest
```

dependency가 Spring AI BOM에서 어떤 버전으로 선택됐는지도 확인합니다.

```text
./gradlew dependencyInsight \
  --dependency mcp-core \
  --configuration runtimeClasspath
```

마지막에는 전체 테스트를 실행합니다.

```text
./gradlew test
```

문서와 whitespace도 확인합니다.

```text
git diff --check
```

## 이번 단계의 완료 기준

M14의 완료 기준은 다음과 같습니다.

```text
Spring AI WebFlux MCP client starter를 사용한다.
MCP client는 기본 비활성화다.
allowlist가 serverName/toolName 단위로 동작한다.
request의 allowMcp 기본값은 false다.
MCP tool 이름은 mcp_<server>_<tool> 형식으로 분리된다.
MCP callback은 기존 ToolRegistry와 ToolPolicy를 통과한다.
원본 ToolDefinition과 JSON Schema가 보존된다.
tool call과 결과가 기존 SSE와 JSONL history에 기록된다.
API key와 외부 MCP server 없이 ./gradlew test가 통과한다.
```

반대로 이번 단계에서 하지 않은 일도 분명합니다.

```text
MCP server exposure
MCP resource와 prompt 연동
sampling
async client
OAuth
AgentRuntime loop 변경
원격 MCP server의 sandbox 구현
```

## Codex에게 줄 작업 프롬프트

```text
Task M14만 수행해줘.

목표:
Spring AI MCP client를 post-MVP adapter로 붙여 외부 MCP tool을 기존 tool/policy 경계 안에서 opt-in으로 사용할 수 있게 해줘.

범위:
- Spring AI MCP client starter 필요 여부와 dependency 이유 문서화
- MCP client 기본 비활성화
- serverName/toolName allowlist 추가
- request의 allowMcp 기본값 false와 명시적 활성화
- MCP tool name prefix와 충돌 방지
- MCP ToolCallback을 내부 AgentTool로 변환
- 기존 ToolExecutionService, ToolPolicy, SSE, JSONL history 재사용
- 원본 ToolDefinition 보존
- fake/mock MCP client 기반 테스트
- README에 MCP client demo 정리

하지 말 것:
- AgentRuntime loop 변경하지 마
- MCP callback을 ChatClient에 직접 넘기지 마
- 기존 built-in tool policy를 약화하지 마
- MCP resources/prompts/sampling 구현하지 마
- async client와 OAuth 추가하지 마
- 원격 MCP server를 테스트에서 호출하지 마

완료 조건:
- MCP disabled 상태에서 기존 built-in tools가 그대로 동작함
- client 활성화, allowlist, allowMcp=true가 모두 있어야 MCP tool이 노출됨
- MCP tool 실행이 기존 policy와 event/session 흐름을 통과함
- API key와 외부 MCP server 없이 ./gradlew test 통과
```

## 마무리

M14에서 중요한 것은 MCP tool을 호출할 수 있게 됐다는 사실만이 아닙니다.

더 중요한 것은 새 통합 기능이 들어와도 기존 runtime의 경계를 유지했다는 점입니다.

```text
연결과 discovery는 Spring AI가 맡는다.
노출 여부는 client 설정, allowlist, allowMcp가 결정한다.
실행은 기존 ToolExecutionService와 ToolPolicy를 통과한다.
관찰과 기록은 기존 SSE와 JSONL을 사용한다.
```

MCP는 기존 tool system을 대체하지 않습니다. 외부 tool을 같은 실행 규칙 안으로 들여오는 adapter입니다.

이 구분이 있어야 integration이 늘어나도 agent runtime을 다시 만들지 않습니다. built-in tool과 외부 tool의 출처는 다르지만, 실행 권한과 기록 방식은 한곳에서 설명할 수 있습니다.

다음 단계인 M15에서는 방향을 반대로 바꿉니다.

이번에는 외부 MCP server의 tool을 가져왔다면, 다음에는 프로젝트의 built-in tool을 MCP server로 노출합니다. 그때도 원칙은 같습니다. 처음부터 모든 tool을 열지 않고, read-only와 명시적 opt-in을 기준으로 작은 범위부터 시작합니다.
