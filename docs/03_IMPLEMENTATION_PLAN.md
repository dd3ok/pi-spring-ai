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
src/main/java/.../tool/ToolRegistry.java
src/main/java/.../tool/builtin/LsTool.java
src/main/java/.../tool/builtin/ReadTool.java
src/main/java/.../tool/builtin/FindTool.java
src/main/java/.../tool/builtin/GrepTool.java
src/test/java/.../tool/builtin/ReadToolTest.java
src/test/java/.../tool/builtin/GrepToolTest.java
```

### 완료 조건

- read/ls/find/grep 테스트 통과
- secret 파일 read 실패
- output limit 적용
- large file 처리 테스트

### 주의

- write/edit/bash 구현하지 않는다.

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
