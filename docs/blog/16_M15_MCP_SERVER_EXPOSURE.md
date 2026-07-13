# M15. built-in tool을 MCP server로 열어도 안전 경계는 그대로다

> 이 글은 `Spring AI로 Pi 스타일 코딩 에이전트 만들기` 시리즈의 일부입니다.
> 목표는 거대한 프레임워크를 한 번에 만드는 것이 아니라, Spring 개발자가 이해할 수 있는 작은 단위로 에이전트 런타임을 쌓아가는 데 있습니다.

## 이번 글의 목적

M14에서는 외부 MCP server의 tool을 가져왔습니다.

Spring AI가 발견한 callback을 바로 모델에 넘기지 않고, `McpAgentTool`로 감싼 뒤 기존 `ToolRegistry`, `ToolExecutionService`, `ToolPolicy` 경계 안으로 넣었습니다.

글의 마지막에는 다음 단계를 이렇게 예고했습니다.

```text
M14: 외부 MCP tool을 가져온다.
M15: built-in tool을 MCP server로 내보낸다.
```

이번에는 방향을 반대로 바꿉니다.

프로젝트가 이미 제공하는 `ls`, `read`, `find`, `grep`, `write`, `edit`, `bash`를 외부 MCP client가 사용할 수 있게 만듭니다. 다만 tool을 새로 구현하거나 기존 runtime을 MCP 중심으로 다시 짜지는 않습니다.

이번 글에서 답할 질문은 다음과 같습니다.

```text
Spring AI MCP server에는 어디까지 맡길 것인가?
built-in tool을 어떻게 기존 policy를 유지한 채 노출할 것인가?
write와 bash는 어떤 조건에서 server tool이 되는가?
MCP server 호출도 SSE와 JSONL session에 기록해야 하는가?
```

## M15에서 만들지 않는 것

MCP server를 추가한다고 해서 MCP의 모든 기능을 구현할 필요는 없습니다.

M15의 목적은 현재 built-in tool을 표준 protocol로 노출하는 한 가지 수직 흐름을 완성하는 것입니다.

```text
MCP resource를 만들지 않는다.
MCP prompt와 completion을 만들지 않는다.
sampling을 구현하지 않는다.
OAuth와 원격 공개 배포를 구현하지 않는다.
remote registry를 만들지 않는다.
plugin system을 만들지 않는다.
AgentRuntime loop를 바꾸지 않는다.
REST/SSE API를 MCP로 교체하지 않는다.
```

외부 client와의 protocol handshake, transport, tool specification 변환은 Spring AI가 담당합니다.

프로젝트가 책임질 부분은 더 좁습니다.

```text
어떤 built-in tool을 노출할지 결정한다.
privileged tool에 필요한 권한을 확인한다.
모든 실행을 기존 policy 경계로 보낸다.
server workspace를 고정한다.
```

## 왜 Spring AI MCP server starter를 사용하는가

MCP server를 직접 만들면 initialize, capability negotiation, `tools/list`, `tools/call`, Streamable HTTP transport를 모두 구현해야 합니다.

이 프로젝트의 목적은 MCP protocol 구현체를 만드는 것이 아닙니다. Spring 개발자가 기존 service와 tool을 MCP 생태계에 연결하는 경계를 설명하는 것이 목적입니다.

그래서 WebFlux 애플리케이션에 맞는 starter를 추가합니다.

```kotlin
implementation("org.springframework.ai:spring-ai-starter-mcp-server-webflux")
```

버전은 Spring AI BOM `2.0.0`이 관리합니다.

Spring AI server starter는 애플리케이션의 `ToolCallback`과 `ToolCallbackProvider`를 모아 MCP tool specification으로 바꿉니다. 이 프로젝트에는 M7부터 사용한 `SpringAiToolCallbackAdapter`가 이미 있습니다.

즉, MCP 전용 tool wrapper를 다시 만들 이유가 없습니다.

```text
AgentTool
  -> SpringAiToolCallbackAdapter
  -> ToolCallbackProvider
  -> Spring AI MCP server auto-configuration
```

`@McpTool` 메서드를 tool마다 새로 만드는 방식도 선택하지 않았습니다.

annotation wrapper를 일곱 개 만들면 이름, 설명, JSON Schema가 기존 `ToolSpec`과 중복됩니다. 더 큰 문제는 wrapper가 `ToolExecutionService`를 빠뜨렸을 때 기존 policy를 쉽게 우회할 수 있다는 점입니다.

현재 추상화가 이미 충분하므로 그대로 재사용합니다.

## 왜 Streamable HTTP를 선택했는가

Spring AI 2.0.0은 WebFlux MCP server에서 Streamable HTTP를 지원합니다. 기존 HTTP+SSE transport는 호환 목적으로 남아 있지만 새 server에는 Streamable HTTP가 권장됩니다.

설정은 다음과 같습니다.

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE
        type: SYNC
        streamable-http:
          mcp-endpoint: /mcp
```

server type은 `SYNC`로 둡니다.

현재 `AgentTool.execute()`와 `ToolExecutionService.execute()`가 동기 계약이기 때문입니다. 내부 구현이 blocking file IO와 process 실행을 service layer에서 다루고 있는데, M15에서 겉만 reactive로 감싸는 것은 구조를 더 복잡하게 만들 뿐입니다.

MCP endpoint는 기존 WebFlux 애플리케이션에 `/mcp` route로 추가됩니다.

```text
REST/SSE client -> /api/sessions/**
MCP client      -> /mcp
```

두 입구는 함께 존재하며 서로를 대체하지 않습니다.

## 전체 실행 흐름

M15의 실행 흐름은 다음과 같습니다.

```text
External MCP client
  -> WebFlux Streamable HTTP /mcp
  -> Spring AI MCP server
  -> McpServerToolCallbackProvider
  -> SpringAiToolCallbackAdapter
  -> ToolExecutionService
  -> ToolPolicy
  -> built-in AgentTool
```

M14와 나란히 놓으면 경계가 더 잘 보입니다.

```text
M14 client adapter
외부 MCP tool -> AgentTool -> 기존 runtime

M15 server adapter
기존 AgentTool -> ToolCallback -> 외부 MCP client
```

방향은 반대지만 실행 정책의 소유자는 같습니다.

## server는 기본 비활성화다

Spring AI MCP server 자체의 기본값만 믿지 않고 애플리케이션 설정에서 명시적으로 끕니다.

```yaml
spring:
  ai:
    mcp:
      server:
        enabled: ${AGENT_MCP_SERVER_ENABLED:false}
```

starter dependency가 classpath에 있다는 사실만으로 source code가 외부에 노출되면 안 됩니다.

server를 사용하려는 운영자가 다음 값을 명시해야 `/mcp`가 활성화됩니다.

```powershell
$env:AGENT_MCP_SERVER_ENABLED = "true"
```

capability도 tool만 켭니다.

```yaml
capabilities:
  resource: false
  tool: true
  prompt: false
  completion: false
```

annotation scanner도 끕니다.

```yaml
annotation-scanner:
  enabled: false
```

이렇게 하면 우연히 추가된 annotation bean이 server capability로 노출되지 않습니다. M15에서 의도한 단 하나의 입구는 `McpServerToolCallbackProvider`입니다.

## 기본 allowlist는 read-only다

서버를 켜더라도 모든 built-in tool을 노출하지 않습니다.

```yaml
agent:
  mcp:
    server:
      exposed-tools: [ls, read, find, grep]
      allow-write: false
      allow-bash: false
```

기본 목록은 프로젝트의 read-only tool 네 개입니다.

```text
ls
read
find
grep
```

Pi가 read-only 도구 묶음을 별도로 구성하고 tool allowlist를 제공하는 것과 같은 방향입니다. 차이는 이 프로젝트가 Spring 설정과 `ToolSafetyLevel`을 함께 사용한다는 점입니다.

allowlist가 중요한 이유는 앞으로 새로운 `AgentTool` bean이 추가되더라도 곧바로 MCP server에 공개되지 않게 하기 위해서입니다.

```text
ToolRegistry에 등록됨 != MCP server에 노출됨
```

## write와 bash는 이중 opt-in이다

`write`, `edit`, `bash`는 이름을 allowlist에 추가하는 것만으로는 부족합니다.

write/edit을 노출하려면 두 조건이 모두 필요합니다.

```text
exposed-tools에 write 또는 edit이 있다.
allow-write=true다.
```

설정 예시는 다음과 같습니다.

```powershell
$env:AGENT_MCP_SERVER_EXPOSED_TOOLS = "ls,read,find,grep,write,edit"
$env:AGENT_MCP_SERVER_ALLOW_WRITE = "true"
```

bash도 같은 방식입니다.

```powershell
$env:AGENT_MCP_SERVER_EXPOSED_TOOLS = "ls,read,find,grep,bash"
$env:AGENT_MCP_SERVER_ALLOW_BASH = "true"
```

allowlist만 추가하고 permission flag를 빠뜨리면 server가 조용히 tool을 숨기지 않습니다. 애플리케이션 시작을 실패시키고 어떤 tool에 권한이 부족한지 알려줍니다.

```text
MCP server tool requires explicit permission: bash
```

설정 오타도 같은 원칙으로 처리합니다.

```text
unknown tool: baash
```

보안 설정에서 잘못된 의도를 추측해 허용하는 것보다, 시작 단계에서 실패하는 편이 낫습니다.

## 설정을 record로 고정한다

server exposure 설정은 작은 record 하나로 받습니다.

```java
@ConfigurationProperties(prefix = "agent.mcp.server")
public record McpServerExposureProperties(
        List<String> exposedTools,
        boolean allowWrite,
        boolean allowBash
) {
}
```

compact constructor에서는 tool 이름을 trim하고, 빈 이름과 중복을 거부합니다.

```text
null 목록 -> read-only 기본 목록
빈 이름 -> 시작 실패
중복 이름 -> 시작 실패
```

별도 builder나 factory는 만들지 않았습니다. Spring configuration binding과 record constructor면 필요한 책임을 충분히 설명할 수 있습니다.

## provider는 기존 adapter를 재사용한다

M15의 핵심 클래스는 `McpServerToolCallbackProvider`입니다.

```java
@Component
@ConditionalOnProperty(
    prefix = "spring.ai.mcp.server",
    name = "enabled",
    havingValue = "true")
public class McpServerToolCallbackProvider implements ToolCallbackProvider {
}
```

server가 비활성화된 상태에서는 이 bean도 만들어지지 않습니다.

provider는 먼저 server permission으로 `AgentRunOptions`를 구성합니다.

```java
new AgentRunOptions(
    properties.allowWrite(),
    properties.allowBash(),
    false,
    defaults.maxTurns(),
    defaults.maxToolCalls(),
    defaults.timeout()
)
```

세 번째 값인 `allowMcp`는 항상 false입니다.

M14에서 가져온 downstream MCP tool을 다시 M15 server로 내보내면 MCP 연결 고리가 생길 수 있습니다. Spring AI의 `expose-mcp-client-tools=false`도 사용하지만, 프로젝트의 server options에서도 한 번 더 차단합니다.

그다음 `ToolRegistry.enabledTools(options)`와 server allowlist를 함께 확인합니다.

```java
properties.exposedTools().stream()
    .map(toolRegistry::get)
    .map(tool -> requireEnabled(tool, enabledTools))
    .map(tool -> new SpringAiToolCallbackAdapter(
        tool,
        toolExecutionService,
        options,
        objectMapper,
        SpringAiToolCallbackEvents.noop()))
```

실행 adapter는 M7부터 사용한 클래스입니다.

따라서 MCP에서 호출한 tool도 다음 경계를 그대로 통과합니다.

```text
ToolExecutionService.before policy
AgentTool 자체의 workspace/secret guard
ToolExecutionService.after output limit
BashCommandPolicy
```

MCP server를 위해 safety 코드를 복사하지 않았습니다.

## workspace는 client가 고르지 못한다

REST agent flow에서는 session을 만들 때 workspace root를 지정합니다.

MCP server 호출에는 이 프로젝트의 `sessionId`가 없습니다. 그렇다고 tool argument에 `workspaceRoot`를 새로 추가하면 외부 client가 server의 실행 경계를 바꿀 수 있습니다.

그래서 server workspace는 애플리케이션 설정에 고정합니다.

```yaml
agent:
  workspace:
    root: .
```

MCP client는 `read`에 `path`만 전달할 수 있습니다.

```json
{
  "path": "README.md"
}
```

`SafePathResolver`는 이 경로를 server의 configured workspace 안에서 해석합니다. `..`, absolute path, symlink escape는 기존과 같은 규칙으로 거부됩니다.

```text
MCP request가 workspace를 선택하지 않는다.
server 운영자가 workspace를 선택한다.
tool은 SafePathResolver 안에서만 실행된다.
```

## MCP server 호출은 agent session이 아니다

M14의 외부 MCP tool 호출은 `AgentRuntime` 안에서 일어났습니다.

그래서 `tool_call_started`, `tool_call_finished`가 SSE로 전송되고 JSONL session history에도 기록됐습니다.

M15는 다릅니다.

외부 MCP client가 `/mcp`로 tool을 직접 호출합니다. 이 요청에는 로컬 agent session과 user message가 없습니다.

```text
M14
REST message -> AgentRuntime -> MCP tool
session 있음, SSE/JSONL 기록 있음

M15
MCP tools/call -> built-in tool
agent session 없음, SSE/JSONL 기록 없음
```

MCP 호출마다 가짜 session을 만들면 session history의 의미가 흐려집니다. 따라서 callback event sink는 `noop`을 사용합니다.

거부 결과는 사라지지 않습니다. 기존 `ToolResult`가 JSON으로 MCP client에 반환됩니다.

별도 MCP audit log나 distributed tracing이 필요하다면 운영 요구가 생긴 뒤 독립 기능으로 추가할 수 있습니다. M15에서는 만들지 않습니다.

## 네트워크 경계도 기본값에 포함한다

MCP server가 read-only라고 해도 source code를 읽을 수 있습니다. 인터넷에 그대로 공개해도 된다는 뜻은 아닙니다.

기본 server address는 loopback으로 둡니다.

```yaml
server:
  address: ${SERVER_ADDRESS:127.0.0.1}
```

M15의 데모 범위는 로컬 MCP client 연결입니다.

```text
http://127.0.0.1:8080/mcp
```

원격 배포에는 인증, TLS, origin 검증, network policy가 추가로 필요합니다. 이 조건을 구현하지 않은 상태에서는 `/mcp`를 외부 네트워크에 직접 공개하지 않습니다.

## 실제 transport로 테스트한다

M14 테스트는 외부 MCP server 대신 fake callback을 사용했습니다.

M15에서는 프로젝트 자체가 server이므로 테스트 안에서 실제 WebFlux server를 띄우고 local MCP client를 연결할 수 있습니다.

```text
SpringBootTest RANDOM_PORT
  -> HttpClientStreamableHttpTransport
  -> McpSyncClient.initialize()
  -> tools/list
  -> tools/call read
```

먼저 server 정보와 기본 tool 목록을 확인합니다.

```java
assertThat(initializeResult.serverInfo().name())
    .isEqualTo("pi-spring-ai");

assertThat(client.listTools().tools())
    .extracting(McpSchema.Tool::name)
    .containsExactlyInAnyOrder("ls", "read", "find", "grep");
```

정상 파일도 읽습니다.

```text
read({"path":"README.md"})
```

그다음 같은 transport를 통해 workspace 밖 접근을 시도합니다.

```text
read({"path":".."})
```

응답의 `ToolResult.error`는 true이고, 이유에는 workspace 밖 경로라는 내용이 포함됩니다.

이 테스트가 중요한 이유는 단순히 `/mcp` route가 열렸다는 사실만 확인하지 않기 때문입니다.

```text
Spring AI protocol 변환이 동작한다.
server allowlist가 적용된다.
실제 tool 호출이 adapter를 통과한다.
기존 workspace safety가 transport 뒤에서도 유지된다.
```

write와 bash의 이중 opt-in은 provider 단위 테스트로 별도 고정합니다.

```text
allowlist에 write만 추가 -> 시작 실패
allow-write=true까지 설정 -> write 노출
allowlist에 bash만 추가 -> 시작 실패
allow-bash=true까지 설정 -> bash 노출
downstream MCP tool 지정 -> 노출 거부
```

외부 LLM API와 원격 MCP server는 호출하지 않습니다.

## 로컬 데모

read-only MCP server를 실행합니다.

```powershell
$env:GEMINI_API_KEY = "not-used-by-mcp-server-demo"
$env:AGENT_MCP_SERVER_ENABLED = "true"
$env:SERVER_ADDRESS = "127.0.0.1"
.\gradlew.bat bootRun
```

현재 애플리케이션은 부팅할 때 `ChatModel` bean도 만들기 때문에 빈 key는 허용하지 않습니다. 위 값은 server-only 데모를 위한 dummy key이며 MCP tool 호출에서는 사용되지 않습니다. agent message API로 실제 모델을 호출할 때는 유효한 provider key가 필요합니다.

MCP client에는 다음 URL을 등록합니다.

```text
http://127.0.0.1:8080/mcp
```

별도 client 설정 없이 transport 흐름만 확인하려면 통합 테스트를 실행합니다.

```powershell
.\gradlew.bat test --tests "com.example.pispringai.mcp.McpServerIntegrationTest"
```

이 테스트는 다음 시나리오를 한 번에 보여줍니다.

```text
initialize
tools/list
README.md read 성공
workspace traversal read 실패
```

## 이번 단계의 완료 기준

M15의 완료 기준은 다음과 같습니다.

```text
Spring AI WebFlux MCP server starter를 사용한다.
server는 기본 비활성화다.
Streamable HTTP /mcp endpoint를 사용한다.
기본 노출은 ls, read, find, grep이다.
write/edit과 bash는 이중 opt-in이다.
downstream MCP tool을 다시 노출하지 않는다.
모든 server tool 호출은 기존 ToolExecutionService와 ToolPolicy를 통과한다.
workspace는 agent.workspace.root에 고정된다.
MCP 호출을 위해 가짜 agent session을 만들지 않는다.
API key와 외부 MCP server 없이 ./gradlew test가 통과한다.
```

반대로 이번 단계에서 하지 않은 일도 분명합니다.

```text
MCP resources/prompts/completion
OAuth와 원격 공개 배포
remote registry
plugin system
MCP 전용 session/audit 저장소
AgentRuntime loop 변경
REST/SSE API 대체
```

## Codex에게 줄 작업 프롬프트

```text
Task M15만 수행해줘.

목표:
현재 built-in tools를 Spring AI MCP server로 노출하되 기존 tool/policy 경계와 read-only 기본값을 유지해줘.

범위:
- Spring AI WebFlux MCP server starter 추가
- Streamable HTTP /mcp endpoint 사용
- MCP server 기본 비활성화
- ls/read/find/grep 기본 노출
- exposed tool allowlist 추가
- write/edit과 bash에 별도 permission flag 적용
- 기존 SpringAiToolCallbackAdapter와 ToolExecutionService 재사용
- server workspace를 agent.workspace.root에 고정
- downstream MCP client tool 재노출 차단
- 실제 local MCP client 기반 통합 테스트
- README와 설계 문서에 server demo와 제한 정리

하지 말 것:
- AgentRuntime loop 변경하지 마
- tool별 @McpTool wrapper를 중복 작성하지 마
- MCP resources/prompts/completion 구현하지 마
- OAuth, remote registry, plugin system 추가하지 마
- MCP 호출을 위해 가짜 agent session을 만들지 마
- bash를 기본 노출하지 마

완료 조건:
- server disabled 상태에서 기존 애플리케이션이 그대로 동작함
- 기본 tools/list에는 read-only tool 네 개만 있음
- write/edit/bash는 이중 opt-in 없이는 노출되지 않음
- MCP tools/call도 기존 workspace와 command policy를 통과함
- API key와 외부 MCP server 없이 ./gradlew test 통과
```

## 마무리

M14와 M15에서 MCP의 양쪽 방향을 모두 연결했습니다.

```text
M14는 외부 tool을 안으로 가져왔다.
M15는 내부 tool을 밖으로 내보냈다.
```

두 단계에서 가장 중요한 것은 MCP 기능의 개수가 아닙니다.

새 protocol을 붙여도 기존 tool abstraction과 safety policy의 소유권이 바뀌지 않았다는 점입니다.

```text
protocol과 transport는 Spring AI가 맡는다.
tool 선택은 명시적 allowlist가 맡는다.
권한은 AgentRunOptions와 ToolRegistry가 맡는다.
실행 안전성은 ToolExecutionService와 ToolPolicy가 맡는다.
workspace 경계는 SafePathResolver가 맡는다.
```

이제 M0부터 M15까지의 본편은 마무리됩니다.

다음 기능을 바로 더하기보다, 필요가 생겼을 때 RAG, tool search, CLI/TUI, LLM-as-Judge 같은 주제를 독립적인 옵션으로 검토할 수 있습니다. 코어 runtime은 작게 유지하고, 확장은 adapter에서 시작한다는 원칙은 그대로 가져갑니다.
