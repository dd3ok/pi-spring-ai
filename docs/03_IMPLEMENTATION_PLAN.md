# 03. Implementation Plan — Codex 작업용 구현 계획

기준 문서: `docs/01_SOT.md`, `docs/02_DESIGN.md`
원칙: 한 번에 하나의 수직 슬라이스만 구현한다. 각 단계는 테스트 또는 실행 가능한 데모를 남긴다.

---

## 전체 마일스톤

```text
M-1. Documentation baseline
M0. Project bootstrap
M1. ChatClient hello
M2. Agent domain model
M3. JSONL session store
M4. Workspace guard
M5. Read-only tools
M6. Tool execution policy
M7. Spring AI tool adapter
M8. AgentRuntime + SSE
M9. Write/Edit tools
M10. Bash tool
M11. README/demo polish
M12. Tool safety hardening
M13. Session history/replay polish
M14. MCP client integration
M15. MCP server exposure
```

---

## M-1. Documentation baseline

### 목표

구현 전에 저장소의 기준 문서와 블로그 작업 리듬을 고정한다.

### 작업

- `docs/`를 source of truth로 둔다.
- `AGENTS.md`를 저장소 루트의 Codex 작업 규칙으로 둔다.
- 루트의 중복 문서 사본은 추적하지 않는다.
- README에 프로젝트 이름, 문서 기준, 작업 순서를 적는다.
- 블로그 시리즈 계획 문서를 추가한다.

### 예상 파일

```text
.gitignore
README.md
AGENTS.md
docs/03_IMPLEMENTATION_PLAN.md
docs/04_CODEX_TASK_PROMPTS.md
docs/05_BLOG_SERIES_PLAN.md
```

### 완료 조건

- clone한 저장소에서 기준 문서를 바로 읽을 수 있다.
- `docs/`와 `AGENTS.md`가 git 추적 대상이다.
- 다음 작업이 `M0 Project bootstrap`임을 README에서 확인할 수 있다.

### 주의

- 아직 Spring Boot 애플리케이션 코드는 만들지 않는다.
- 기존 루트 중복 문서는 수정하지 않는다.

---

## M0. Project bootstrap

### 목표

Spring Boot + Spring AI 프로젝트 골격을 만든다.

### 작업

- Gradle Kotlin DSL 프로젝트 생성
- Java 21 설정
- Spring Boot 4.x 설정
- Spring AI BOM 설정
- WebFlux dependency 추가
- 기본 application.yml 작성
- health endpoint 확인

### 예상 파일

```text
settings.gradle.kts
build.gradle.kts
src/main/java/.../PiSpringAiApplication.java
src/main/resources/application.yml
src/test/java/.../PiSpringAiApplicationTests.java
```

### 완료 조건

- `./gradlew test` 통과
- 애플리케이션 시작 가능
- README에 실행 방법 초안 추가

### 주의

- 아직 tool, session, runtime 구현하지 않는다.
- production dependency는 최소화한다.

---

## M1. ChatClient hello

### 목표

Spring AI `ChatClient`로 최소 LLM 호출 endpoint를 만든다.

### 작업

- `ChatClient` Bean 구성
- `AiChatController` 작성
- `POST /api/chat` 구현
- request/response DTO 작성

### 예상 API

```http
POST /api/chat
Content-Type: application/json

{"text":"hello"}
```

응답:

```json
{"content":"..."}
```

### 예상 파일

```text
src/main/java/.../api/AiChatController.java
src/main/java/.../api/ChatRequest.java
src/main/java/.../api/ChatResponse.java
src/main/java/.../model/ChatClientConfig.java
```

### 완료 조건

- mock 또는 slice test 추가
- 실제 API key가 없어도 test 통과
- README에 환경변수 설명 추가

---

## M2. Agent domain model

### 목표

Agent의 핵심 타입을 만든다. 아직 LLM과 연결하지 않는다.

### 작업

- `AgentMessage` sealed interface
- `UserMessage`, `AssistantMessage`, `ToolResultMessage`
- `AgentEvent` sealed interface
- 주요 event record 추가
- `AgentRunOptions`
- `AgentPromptCommand`
- ID 생성 유틸리티

### 예상 파일

```text
src/main/java/.../agent/message/AgentMessage.java
src/main/java/.../agent/event/AgentEvent.java
src/main/java/.../agent/AgentRunOptions.java
src/main/java/.../agent/AgentPromptCommand.java
src/main/java/.../agent/IdGenerator.java
```

### 완료 조건

- 타입 생성 테스트
- Jackson 직렬화가 필요한 경우 테스트 추가
- 불필요한 interface 금지

---

## M3. JSONL session store

### 목표

세션과 메시지를 파일 기반 JSONL로 저장한다.

### 작업

- `AgentSession` record
- `SessionStore` interface
- `JsonlSessionStore` 구현
- `StoredMessage`, `StoredEvent` load helper DTO
- session metadata 저장
- messages.jsonl append/load
- events.jsonl append/load

### 예상 파일

```text
src/main/java/.../session/AgentSession.java
src/main/java/.../session/SessionStore.java
src/main/java/.../session/JsonlSessionStore.java
src/main/java/.../session/SessionProperties.java
src/main/java/.../session/StoredMessage.java
src/main/java/.../session/StoredEvent.java
src/main/java/.../session/SessionStoreException.java
src/test/java/.../session/JsonlSessionStoreTest.java
```

### 완료 조건

- 세션 생성 테스트
- 메시지 append/load 테스트
- 이벤트 append/load 테스트
- 사람이 읽을 수 있는 JSONL 포맷
- `kind`/`type` 값으로 concrete message/event를 복원
- 알 수 없는 `kind`/`type`은 명시적으로 실패
- JSONL은 flat concrete object이며 `payload` wrapper를 쓰지 않음
- `kind`/`type` 누락, 문자열이 아닌 discriminator, malformed JSONL 실패
- append 순서 보존
- 없는 session에 대한 append/load 실패와 `findSession` empty
- sessionId path traversal 차단
- 손상된 `session.json` 실패

### 주의

- 처음부터 DB 붙이지 않는다.
- Jackson `@JsonTypeInfo`를 도메인 sealed interface에 붙이지 않는다.
- M3에서는 root JSON object를 담는 load helper DTO와 명시적 switch 매핑을 사용한다.
- `StoredMessage`, `StoredEvent`는 on-disk wrapper가 아니다. 파일에는 M2 concrete JSON shape 그대로 쓴다.
- JSONL load 순서는 `readTree(line)` -> discriminator 추출 -> `StoredMessage`/`StoredEvent` 생성 -> switch -> `treeToValue(root, ConcreteRecord.class)`로 고정한다.
- flat JSONL line을 `StoredMessage`/`StoredEvent`로 직접 역직렬화하지 않는다. 파일에는 `json` 필드가 없다.
- AgentRuntime, API, SSE와 연결하지 않는다.
- branch/fork, compaction은 구현하지 않는다.

---

## M4. Workspace guard

### 목표

파일 도구의 안전한 경로 처리를 구현한다.

### 작업

- `WorkspaceProperties`
- `SafePathResolver`
- `SecretFileGuard`
- `WorkspaceAccessDeniedException`
- path traversal 테스트
- secret pattern 테스트

### 예상 파일

```text
src/main/java/.../workspace/WorkspaceProperties.java
src/main/java/.../workspace/SafePathResolver.java
src/main/java/.../workspace/SecretFileGuard.java
src/main/java/.../workspace/WorkspaceAccessDeniedException.java
src/test/java/.../workspace/SafePathResolverTest.java
src/test/java/.../workspace/SecretFileGuardTest.java
```

### 완료 조건

- `README.md` 같은 내부 파일 허용
- `../outside.txt` 차단
- `.env` 차단
- symlink escape 가능성에 대한 TODO 또는 테스트 작성

---

## M5. Read-only tools

### 목표

읽기 전용 도구를 구현한다.

### 작업

- `AgentTool` interface
- `ToolSpec`, `ToolArguments`, `ToolResult`
- `ToolSafetyLevel` 또는 read-only 여부를 표현하는 최소 타입
- `ToolProperties` (`agent.tools.max-tool-output-chars`)
- `LsTool`
- `ReadTool`
- `FindTool`
- `GrepTool`
- `ToolRegistry`

### 예상 파일

```text
src/main/java/.../tool/AgentTool.java
src/main/java/.../tool/ToolSpec.java
src/main/java/.../tool/ToolArguments.java
src/main/java/.../tool/ToolResult.java
src/main/java/.../tool/ToolSafetyLevel.java
src/main/java/.../tool/ToolProperties.java
src/main/java/.../tool/ToolRegistry.java
src/main/java/.../tool/builtin/LsTool.java
src/main/java/.../tool/builtin/ReadTool.java
src/main/java/.../tool/builtin/FindTool.java
src/main/java/.../tool/builtin/GrepTool.java
src/test/java/.../tool/ToolArgumentsTest.java
src/test/java/.../tool/ToolRegistryTest.java
src/test/java/.../tool/builtin/LsToolTest.java
src/test/java/.../tool/builtin/ReadToolTest.java
src/test/java/.../tool/builtin/FindToolTest.java
src/test/java/.../tool/builtin/GrepToolTest.java
```

### 완료 조건

- read/ls/find/grep 테스트 통과
- 모든 파일 경로가 `SafePathResolver.resolveExisting()`을 통과
- `SecretFileGuard`가 read뿐 아니라 ls/find/grep 결과에도 적용
- secret 파일 read 실패
- ls/find/grep 결과에서 secret path 제외
- read는 UTF-8 텍스트 파일만 허용하고 large/binary file은 실패
- grep은 large/binary file을 skip하고 metadata에 skip count 기록
- read/list/grep 개별 제한과 `max-tool-output-chars` output limit 적용
- large file 처리 테스트

### 주의

- write/edit/bash 구현하지 않는다.
- `ToolPolicy`, `ToolPolicyDecision`, `DefaultToolPolicy`, `ToolExecutionContext`, `ToolExecutionService`는 M6로 남긴다.
- Spring AI adapter를 구현하지 않는다.
- M5의 `AgentTool`은 `execute(ToolArguments arguments)`처럼 context 없는 작은 계약으로 시작해도 된다.
- `FindTool`은 glob 기반으로 시작한다. `*.java`는 파일명 기준 매칭, `src/**/*.java`는 상대 경로 기준 매칭으로 테스트에 고정한다.
- `GrepTool`은 regex가 아니라 literal substring 검색으로 시작한다.

---

## M6. Tool execution policy

### 목표

도구 실행 전후 정책을 붙인다.

### 작업

- `ToolPolicy`
- `ToolPolicyDecision`
- `DefaultToolPolicy`
- `ToolExecutionContext`
- `ToolExecutionService`
- allowWrite/allowBash 옵션 반영

### 예상 파일

```text
src/main/java/.../tool/policy/ToolPolicy.java
src/main/java/.../tool/policy/ToolPolicyDecision.java
src/main/java/.../tool/policy/DefaultToolPolicy.java
src/main/java/.../tool/ToolExecutionContext.java
src/main/java/.../tool/ToolExecutionService.java
src/test/java/.../tool/policy/DefaultToolPolicyTest.java
```

### 완료 조건

- read-only tool 허용
- write tool은 allowWrite=false에서 거부
- bash tool은 allowBash=false에서 거부
- max output truncation 테스트

---

## M7. Spring AI tool adapter

### 목표

내부 `AgentTool`을 Spring AI tool calling에 연결한다.

### 작업

- `SpringAiToolCallbackAdapter`
- `ToolCallbackProvider` 또는 adapter factory
- tool call start/end/denied event를 emit할 수 있는 hook
- Spring AI API에 맞춰 실제 구현

### 예상 파일

```text
src/main/java/.../model/SpringAiToolCallbackAdapter.java
src/main/java/.../model/SpringAiToolCallbackFactory.java
src/test/java/.../model/SpringAiToolCallbackAdapterTest.java
```

### 완료 조건

- 내부 AgentTool이 Spring AI tool로 노출된다.
- tool 실행 시 ToolExecutionService를 거친다.
- policy denied 결과가 모델에게 error result로 전달된다.

### 주의

- Spring AI의 실제 API를 확인하고 구현한다.
- API가 복잡하면 먼저 `@Tool` 기반 간단 adapter로 시작해도 된다.
- 단, 내부 tool/policy 구조는 유지한다.

---

## M8. AgentRuntime + SSE

### 목표

사용자가 메시지를 보내면 agent event stream이 흘러나오게 한다.

### 작업

- `AgentRuntime` interface
- `DefaultAgentRuntime`
- `AgentController`
- `POST /api/sessions`
- `POST /api/sessions/{id}/messages/stream`
- SSE event serialization
- session append

### 예상 파일

```text
src/main/java/.../agent/AgentRuntime.java
src/main/java/.../agent/DefaultAgentRuntime.java
src/main/java/.../api/AgentController.java
src/main/java/.../api/CreateSessionRequest.java
src/main/java/.../api/CreateSessionResponse.java
src/main/java/.../api/AgentPromptRequest.java
src/test/java/.../api/AgentApiSmokeTest.java
```

### 완료 조건

- 세션 생성 가능
- 메시지 stream endpoint 호출 가능
- 최소 agent_started/user_message_appended/assistant_completed 이벤트 발생
- 도구가 호출되면 tool event도 발생
- JSONL에 메시지와 이벤트 기록

---

## M9. Write/Edit tools

### 목표

쓰기 도구를 안전 정책 뒤에 추가한다.

### 작업

- `WriteTool`
- `EditTool`
- overwrite policy
- exact oldText replace
- allowWrite option 연결

### 예상 파일

```text
src/main/java/.../tool/builtin/WriteTool.java
src/main/java/.../tool/builtin/EditTool.java
src/test/java/.../tool/builtin/WriteToolTest.java
src/test/java/.../tool/builtin/EditToolTest.java
```

### 완료 조건

- allowWrite=false에서 거부
- allowWrite=true에서 새 파일 쓰기 가능
- 기존 파일 overwrite는 명시 옵션 없으면 거부
- EditTool은 oldText가 정확히 한 번일 때만 수정

---

## M10. Bash tool

### 목표

명령 실행 도구를 가장 보수적으로 추가한다.

### 작업

- `BashTool`
- timeout
- working directory 고정
- output limit
- destructive command denylist
- allowBash option 연결

### 예상 파일

```text
src/main/java/.../tool/builtin/BashTool.java
src/main/java/.../tool/policy/BashCommandPolicy.java
src/test/java/.../tool/builtin/BashToolTest.java
src/test/java/.../tool/policy/BashCommandPolicyTest.java
```

### 완료 조건

- allowBash=false에서 거부
- `pwd`, `./gradlew test` 같은 명령 실행 가능
- `rm -rf`, `git reset --hard`, secret 출력 가능 명령은 기본 거부
- timeout 테스트

### 주의

- bash는 MVP에서도 optional로 둔다.
- README에는 위험성을 명확히 쓴다.

---

## M11. README/demo polish

### 목표

포트폴리오로 볼 수 있는 문서를 정리한다.

### 작업

- README 작성
- architecture diagram text 추가
- 실행 방법
- 데모 시나리오
- 안전 정책 설명
- 한계와 다음 단계

### 완료 조건

README만 보고 다음을 이해할 수 있다.

- 이 프로젝트가 무엇인지
- 왜 Spring AI로 만들었는지
- 어떻게 실행하는지
- 어떤 도구가 있는지
- 안전장치가 무엇인지
- 다음에 무엇을 만들지

---

## M12. Tool safety hardening

### 목표

이미 구현된 read-only/write/edit/bash 도구의 안전 정책을 테스트와 문서로 더 단단히
고정한다.

### 작업

- path traversal, workspace 밖 접근, secret path 차단 사례 보강
- write/edit overwrite와 exact match 실패 사례 보강
- bash destructive command, timeout, output limit 사례 보강
- policy denied가 SSE event와 JSONL history에 남는지 확인
- README safety 설명 보완

### 하지 말 것

- 새 built-in tool을 추가하지 않는다.
- AgentRuntime loop를 변경하지 않는다.
- MCP를 붙이지 않는다.
- 새 production dependency를 추가하지 않는다.

### 완료 조건

- tool/policy deny 사례가 테스트로 고정된다.
- API key 없이 `./gradlew test`가 통과한다.
- README만 보고 safety model을 설명할 수 있다.

---

## M13. Session history/replay polish

### 목표

SSE event와 JSONL session history를 사람이 조회하고 재현 가능한 흐름으로 정리한다.

### 작업

- session 조회 또는 history 확인 흐름 정리
- messages.jsonl/events.jsonl 예시 보강
- SSE event와 JSONL record의 관계 문서화
- replay/debug demo 시나리오 추가
- 필요한 경우 좁은 smoke test 추가

### 하지 말 것

- DB를 붙이지 않는다.
- branch/fork를 구현하지 않는다.
- compaction을 구현하지 않는다.
- Spring AI ChatMemory를 session history 대체재로 사용하지 않는다.

### 완료 조건

- session 생성부터 event 기록까지 README/docs만 보고 따라갈 수 있다.
- JSONL history가 debugging/replay 자료라는 점이 명확하다.
- API key 없이 `./gradlew test`가 통과한다.

---

## M14. MCP client integration

### 목표

Spring AI MCP client를 post-MVP adapter로 붙여 외부 MCP tool을 기존 tool 실행 경계
안에서 opt-in으로 사용할 수 있게 한다.

### 작업

- Spring AI MCP client starter 필요 여부와 dependency 이유 문서화
- MCP client 기본 비활성 또는 명시 설정 기반 활성화
- tool filtering/name prefix 정책 정리
- MCP tool 실행이 기존 `ToolPolicy`를 우회하지 않도록 adapter 설계
- fake/local MCP server 기반 테스트

### 하지 말 것

- MCP를 core tool system으로 바꾸지 않는다.
- dynamic plugin marketplace를 만들지 않는다.
- 외부 네트워크나 API key가 필요한 테스트를 만들지 않는다.
- RAG/Vector DB를 함께 붙이지 않는다.

### 완료 조건

- MCP disabled 상태에서 기존 built-in tools가 그대로 동작한다.
- MCP enabled demo가 기존 event/session 흐름에 기록된다.
- API key 없이 `./gradlew test`가 통과한다.

### 구현 결정

- WebFlux 애플리케이션과 맞는 `spring-ai-starter-mcp-client-webflux`를 사용한다.
- MCP client는 설정에서 기본 비활성화하고, tool은 allowlist와 `allowMcp=true`를 모두 요구한다.
- Spring AI의 `McpToolFilter`, `McpToolNamePrefixGenerator`, `SyncMcpToolCallbackProvider`를 사용한다.
- MCP callback은 request 시점에 조회해 `McpAgentTool`로 감싼 뒤 기존 registry/policy를 통과시킨다.
- MCP callback의 원본 `ToolDefinition`을 유지한다.
- 테스트에서는 fake MCP callback/provider를 사용하고 외부 transport를 호출하지 않는다.

---

## M15. MCP server exposure

### 목표

현재 built-in tools를 Spring AI MCP server로 노출해, 이 프로젝트가 MCP ecosystem의
server 역할도 할 수 있음을 보여준다.

### 작업

- Spring AI MCP server starter 필요 여부와 dependency 이유 문서화
- read-only tools를 우선 노출
- write/edit/bash는 opt-in 정책을 유지
- MCP server 호출도 workspace safety와 command policy를 통과하게 구성
- README에 MCP client/server demo 정리

### 하지 말 것

- remote registry, plugin system, multi-agent를 만들지 않는다.
- MCP server가 기존 REST/SSE API를 대체하게 하지 않는다.
- bash를 기본 노출하지 않는다.

### 완료 조건

- read-only MCP server demo가 가능하다.
- write/edit/bash는 명시 opt-in 없이는 노출 또는 실행되지 않는다.
- API key 없이 `./gradlew test`가 통과한다.

### 구현 결정

- WebFlux Streamable HTTP `/mcp`와 동기 `ToolCallbackProvider`를 사용한다.
- server, annotation scanner, privileged tool은 기본 비활성화한다.
- `McpServerToolCallbackProvider`가 allowlist를 적용한 built-in tool만 기존
  `SpringAiToolCallbackAdapter`로 변환한다.
- server workspace는 `agent.workspace.root`에 고정하고 MCP client 입력으로 받지 않는다.
- downstream MCP tool, resources, prompts, completion은 노출하지 않는다.
- 실제 local MCP client 통합 테스트로 initialize, tools/list, read, traversal deny를 검증한다.

상태: 완료

---

## 시즌 2 또는 옵션 backlog

본편에서 제외하고 별도 판단 후 다룬다.

- RAG/Vector DB
- Tool Search Advisor
- CLI/TUI/dashboard
- LLM-as-Judge evaluation
- session branch/fork
- compaction
- multi-agent
- plugin/package system

---

## Codex 작업 검토 체크리스트

각 마일스톤 완료 후 확인한다.

```text
[ ] SOT 범위를 벗어난 구현이 없는가?
[ ] 새 dependency가 있다면 이유가 문서화되었는가?
[ ] 테스트가 추가되었는가?
[ ] 외부 LLM API 없이 테스트가 통과하는가?
[ ] 보안 기본값이 fail-closed인가?
[ ] README 또는 문서 업데이트가 필요한가?
[ ] 다음 작업으로 넘길 TODO가 명확한가?
```
