# M15. built-in tool을 MCP server로 열어도 안전 경계는 그대로다

> 이 글은 `Spring AI로 Pi 스타일 코딩 에이전트 만들기` 시리즈의 일부입니다.
> 목표는 거대한 프레임워크를 한 번에 만드는 것이 아니라, Spring 개발자가 이해할 수 있는 작은 단위로 에이전트 런타임을 쌓아가는 데 있습니다.

## 이번 글의 목적

M14에서는 외부 MCP server의 tool을 에이전트 안으로 가져왔습니다.

Spring AI가 발견한 callback은 모델에 바로 넘기지 않았습니다. `McpAgentTool`로 감싼 뒤 기존 `ToolRegistry`, `ToolExecutionService`, `ToolPolicy` 경계 안에 넣었습니다.

글의 마지막에는 다음 단계를 이렇게 예고했습니다.

```text
M14: 외부 MCP tool을 가져온다.
M15: built-in tool을 MCP server로 내보낸다.
```

M15에서는 방향이 반대입니다.

프로젝트가 이미 제공하는 `ls`, `read`, `find`, `grep`, `write`, `edit`, `bash`를 외부 MCP client에 공개합니다. 그렇다고 tool을 새로 구현하거나 기존 runtime을 MCP 중심으로 다시 짜지는 않습니다.

이번 글에서는 네 가지를 확인합니다.

```text
Spring AI MCP server에는 어디까지 맡길 것인가?
built-in tool을 어떻게 기존 policy를 유지한 채 노출할 것인가?
write와 bash는 어떤 조건에서 server tool이 되는가?
MCP server 호출도 SSE와 JSONL session에 기록해야 하는가?
```

## M15에서 만들지 않는 것

MCP server를 추가한다고 MCP의 모든 기능까지 구현할 필요는 없습니다.

M15에서는 현재 built-in tool을 표준 protocol로 노출하는 수직 흐름 하나만 완성합니다.

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

외부 client와 주고받는 protocol handshake, transport, tool specification 변환은 Spring AI에 맡깁니다.

프로젝트의 책임은 그보다 좁게 잡았습니다.

```text
어떤 built-in tool을 노출할지 결정한다.
privileged tool에 필요한 권한을 확인한다.
모든 실행을 기존 policy 경계로 보낸다.
server workspace를 고정한다.
```

## 왜 Spring AI MCP server starter를 사용하는가

MCP server를 직접 만들면 initialize, capability negotiation, `tools/list`, `tools/call`, Streamable HTTP transport를 모두 구현해야 합니다.

이 프로젝트는 MCP protocol 구현체를 만들려는 것이 아닙니다. Spring 개발자가 기존 service와 tool을 MCP 생태계에 연결할 때 어디에 경계를 둘지 설명하는 데 초점을 맞춥니다.

WebFlux 애플리케이션에 맞는 starter를 선택한 배경입니다.

```kotlin
implementation("org.springframework.ai:spring-ai-starter-mcp-server-webflux")
```

버전은 Spring AI BOM `2.0.0`이 관리합니다.

Spring AI server starter는 애플리케이션의 `ToolCallback`과 `ToolCallbackProvider`를 모아 MCP tool specification으로 바꿉니다. 이 프로젝트에는 M7부터 써 온 `SpringAiToolCallbackAdapter`가 이미 있습니다.

MCP 전용 tool wrapper를 다시 만들 필요는 없습니다.

```text
AgentTool
  -> SpringAiToolCallbackAdapter
  -> ToolCallbackProvider
  -> Spring AI MCP server auto-configuration
```

`@McpTool` 메서드를 tool마다 새로 만드는 방식도 선택하지 않았습니다.

annotation wrapper를 일곱 개 만들면 이름, 설명, JSON Schema가 기존 `ToolSpec`과 겹칩니다. 더 위험한 문제는 따로 있습니다. wrapper에서 `ToolExecutionService`를 빠뜨리면 기존 policy를 우회하게 됩니다.

이미 있는 추상화로 필요한 역할을 설명할 수 있으니 그대로 재사용합니다.

## 왜 Streamable HTTP를 선택했는가

Spring AI 2.0.0은 WebFlux MCP server에서 Streamable HTTP를 지원합니다. 기존 HTTP+SSE transport도 호환을 위해 남아 있지만, 새 server에는 Streamable HTTP가 권장됩니다.

설정은 아래와 같습니다.

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

현재 `AgentTool.execute()`와 `ToolExecutionService.execute()`가 동기 계약이기 때문입니다. 내부 구현도 blocking file IO와 process 실행을 service layer에서 다룹니다. M15에서 겉만 reactive로 감싸면 구조만 더 복잡해집니다.

MCP endpoint는 기존 WebFlux 애플리케이션에 `/mcp` route로 추가됩니다.

```text
REST/SSE client -> /api/sessions/**
MCP client      -> /mcp
```

두 입구는 함께 열리며 어느 한쪽이 다른 쪽을 대체하지 않습니다.

## 전체 실행 흐름

M15의 실행 흐름을 한 줄로 잇으면 이렇습니다.

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

M14의 흐름과 나란히 놓으면 경계가 선명해집니다.

```text
M14 client adapter
외부 MCP tool -> AgentTool -> 기존 runtime

M15 server adapter
기존 AgentTool -> ToolCallback -> 외부 MCP client
```

흐르는 방향만 다를 뿐, 실행 정책의 소유자는 같습니다.

## server는 기본 비활성화다

Spring AI MCP server의 기본값에 기대지 않고 애플리케이션 설정에서도 명시적으로 끕니다.

```yaml
spring:
  ai:
    mcp:
      server:
        enabled: ${AGENT_MCP_SERVER_ENABLED:false}
```

starter dependency가 classpath에 들어왔다는 이유만으로 source code가 외부에 노출되어서는 안 됩니다.

운영자가 다음 값을 명시해야 비로소 `/mcp`가 활성화됩니다.

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

이 설정은 의도치 않게 추가된 annotation bean이 server capability로 노출되는 일을 막습니다. M15에서 허용한 입구는 `McpServerToolCallbackProvider` 하나뿐입니다.

## 기본 allowlist는 read-only다

server를 켰다고 모든 built-in tool이 함께 열리지는 않습니다.

```yaml
agent:
  mcp:
    server:
      exposed-tools: [ls, read, find, grep]
      allow-write: false
      allow-bash: false
```

기본 목록에는 프로젝트의 read-only tool 네 개만 들어갑니다.

```text
ls
read
find
grep
```

Pi가 read-only 도구 묶음을 따로 구성하고 tool allowlist를 제공하는 것과 같은 방향입니다. 이 프로젝트에서는 Spring 설정과 `ToolSafetyLevel`을 함께 사용한다는 차이가 있습니다.

allowlist가 있으면 앞으로 새로운 `AgentTool` bean이 추가돼도 곧바로 MCP server에 공개되지 않습니다.

```text
ToolRegistry에 등록됨 != MCP server에 노출됨
```

## write와 bash는 이중 opt-in이다

`write`, `edit`, `bash`는 이름을 allowlist에 추가하는 것만으로는 부족합니다.

write/edit을 노출하려면 아래 두 조건을 모두 만족해야 합니다.

```text
exposed-tools에 write 또는 edit이 있다.
allow-write=true다.
```

설정은 이렇게 켭니다.

```powershell
$env:AGENT_MCP_SERVER_EXPOSED_TOOLS = "ls,read,find,grep,write,edit"
$env:AGENT_MCP_SERVER_ALLOW_WRITE = "true"
```

bash도 같은 방식입니다.

```powershell
$env:AGENT_MCP_SERVER_EXPOSED_TOOLS = "ls,read,find,grep,bash"
$env:AGENT_MCP_SERVER_ALLOW_BASH = "true"
```

allowlist에 이름만 추가하고 permission flag를 빠뜨리면 server가 해당 tool을 조용히 숨기지는 않습니다. 애플리케이션 시작을 중단하고 어느 tool에 권한이 부족한지 알려줍니다.

```text
MCP server tool requires explicit permission: bash
```

설정 오타도 같은 원칙으로 처리합니다.

```text
unknown tool: baash
```

보안 설정은 잘못된 의도를 추측해 허용하기보다 시작 단계에서 실패하는 편이 낫습니다.

## 설정을 record로 고정한다

server 노출 설정은 작은 record 하나에 모았습니다.

```java
@ConfigurationProperties(prefix = "agent.mcp.server")
public record McpServerExposureProperties(
        List<String> exposedTools,
        boolean allowWrite,
        boolean allowBash
) {
}
```

compact constructor는 tool 이름을 trim하고 빈 이름과 중복을 거부합니다.

```text
null 목록 -> read-only 기본 목록
빈 이름 -> 시작 실패
중복 이름 -> 시작 실패
```

별도 builder나 factory는 만들지 않았습니다. Spring configuration binding과 record constructor만으로 필요한 책임이 충분히 드러납니다.

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

server가 비활성화되어 있으면 이 bean도 만들어지지 않습니다.

provider는 server permission을 바탕으로 `AgentRunOptions`부터 구성합니다.

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

M14에서 가져온 downstream MCP tool을 M15 server로 다시 내보내면 MCP 연결 고리가 생길 수 있습니다. Spring AI의 `expose-mcp-client-tools=false`를 사용하고, 프로젝트의 server options에서도 한 번 더 막습니다.

그다음 `ToolRegistry.enabledTools(options)`와 server allowlist를 맞춰 봅니다.

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

실행 adapter는 M7부터 쓰던 클래스입니다.

MCP에서 호출한 tool도 같은 경계를 그대로 통과합니다.

```text
ToolExecutionService.before policy
AgentTool 자체의 workspace/secret guard
ToolExecutionService.after output limit
BashCommandPolicy
```

MCP server를 위해 safety 코드를 복사하지 않았습니다.

## workspace는 client가 고르지 못한다

REST agent flow는 session을 만들 때 workspace root를 정합니다.

MCP server 호출에는 이 프로젝트의 `sessionId`가 없습니다. 여기에 `workspaceRoot`를 tool argument로 추가하면 외부 client가 server의 실행 경계를 바꿀 수 있습니다.

따라서 server workspace는 애플리케이션 설정에 고정합니다.

```yaml
agent:
  workspace:
    root: .
```

MCP client가 `read`에 넘길 수 있는 값은 `path`뿐입니다.

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

M14에서는 외부 MCP tool을 `AgentRuntime` 안에서 호출했습니다.

그 과정에서 `tool_call_started`, `tool_call_finished` event가 SSE로 전송됐고 JSONL session history에도 남았습니다.

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

MCP 호출마다 가짜 session을 만들면 session history의 의미가 흐려집니다. callback event sink에 `noop`을 사용한 이유입니다.

거부 결과는 사라지지 않습니다. 기존 `ToolResult`가 JSON으로 MCP client에 반환됩니다.

별도 MCP audit log나 distributed tracing은 실제 운영 요구가 생겼을 때 독립 기능으로 추가하면 됩니다. M15의 범위에는 넣지 않았습니다.

## 네트워크 경계도 기본값에 포함한다

MCP server가 read-only여도 source code는 읽을 수 있습니다. 인터넷에 그대로 공개해도 된다는 뜻은 아닙니다.

기본 server address는 loopback으로 둡니다.

```yaml
server:
  address: ${SERVER_ADDRESS:127.0.0.1}
```

M15의 데모 범위는 로컬 MCP client 연결입니다.

```text
http://127.0.0.1:8080/mcp
```

원격 배포에는 인증, TLS, origin 검증, network policy가 더 필요합니다. 이 조건을 갖추기 전에는 `/mcp`를 외부 네트워크에 직접 공개하지 않습니다.

## 실제 transport로 테스트한다

M14 테스트는 외부 MCP server 대신 fake callback을 사용했습니다.

M15에서는 프로젝트 자체가 server입니다. 테스트 안에서 실제 WebFlux server를 띄우고 local MCP client를 연결합니다.

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

그다음 같은 transport로 workspace 밖 접근을 시도합니다.

```text
read({"path":".."})
```

응답에서는 `ToolResult.error`가 true이며, 오류 이유에 workspace 밖 경로라는 내용이 담깁니다.

이 테스트는 `/mcp` route가 열렸다는 사실만 확인하지 않습니다.

```text
Spring AI protocol 변환이 동작한다.
server allowlist가 적용된다.
실제 tool 호출이 adapter를 통과한다.
기존 workspace safety가 transport 뒤에서도 유지된다.
```

write와 bash의 이중 opt-in은 provider 단위 테스트로 따로 고정합니다.

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

현재 애플리케이션은 부팅할 때 `ChatModel` bean도 만들기 때문에 빈 key를 허용하지 않습니다. 위 값은 server-only 데모에 쓰는 dummy key이며 MCP tool 호출에서는 사용되지 않습니다. agent message API로 실제 모델을 호출할 때는 유효한 provider key가 필요합니다.

MCP client에는 다음 URL을 등록합니다.

```text
http://127.0.0.1:8080/mcp
```

별도 client를 설정하지 않고 transport 흐름만 보려면 통합 테스트를 실행합니다.

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

M15는 아래 조건을 만족하면 완료입니다.

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

이번 단계에서 제외한 범위도 명확히 남깁니다.

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

두 단계의 핵심은 MCP 기능의 개수가 아닙니다. 새 protocol을 붙여도 기존 tool abstraction과 safety policy의 소유권은 바뀌지 않습니다.

```text
protocol과 transport는 Spring AI가 맡는다.
tool 선택은 명시적 allowlist가 맡는다.
권한은 AgentRunOptions와 ToolRegistry가 맡는다.
실행 안전성은 ToolExecutionService와 ToolPolicy가 맡는다.
workspace 경계는 SafePathResolver가 맡는다.
```

이제 M0부터 M15까지의 본편은 마무리됩니다.

다음 기능을 바로 더하기보다 필요가 생겼을 때 RAG, tool search, CLI/TUI, LLM-as-Judge를 각각 독립적인 옵션으로 검토하면 됩니다. 코어 runtime은 작게 유지하고 확장은 adapter에서 시작한다는 원칙은 그대로 가져갑니다.
