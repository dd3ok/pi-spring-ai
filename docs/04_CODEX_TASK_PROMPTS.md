# 04. Codex Task Prompts

이 문서는 Codex에게 그대로 복사해서 줄 수 있는 작업 프롬프트 모음이다.
각 작업은 반드시 `docs/01_SOT.md`, `docs/02_DESIGN.md`, `docs/03_IMPLEMENTATION_PLAN.md`를 기준으로 수행한다.

---

## 공통 프롬프트 헤더

모든 작업 요청 앞에 붙인다.

```text
프로젝트 기준 문서를 먼저 읽어줘:
- docs/01_SOT.md
- docs/02_DESIGN.md
- docs/03_IMPLEMENTATION_PLAN.md
- AGENTS.md

중요 원칙:
- MVP 범위를 넘지 마.
- 새 production dependency는 반드시 이유를 먼저 설명하고, 가능하면 추가하지 마.
- 외부 LLM API를 실제 테스트에서 호출하지 마.
- 보안 기본값은 fail-closed로 둬.
- 변경 후 실행한 테스트와 결과를 요약해줘.
```

---

## Task M-1 — Documentation baseline

```text
Task M-1만 구현해줘.

목표:
구현 전에 저장소 기준 문서와 블로그 작업 리듬을 고정해줘.

범위:
- docs/를 source of truth로 둔다.
- AGENTS.md를 루트 작업 규칙으로 둔다.
- README에 프로젝트 이름, 문서 기준, 작업 순서를 정리한다.
- docs/05_BLOG_SERIES_PLAN.md에 블로그 시리즈 목차를 작성한다.
- .gitignore에서 docs/와 AGENTS.md는 추적 가능하게 둔다.

하지 말 것:
- Spring Boot 애플리케이션 코드를 만들지 마
- 기존 루트 중복 문서를 수정하지 마
- 새 production dependency를 추가하지 마

완료 조건:
- docs/와 AGENTS.md가 git 추적 대상임
- README에서 다음 작업이 M0임을 확인할 수 있음
- 구현 코드는 아직 없음
```

---

## Task M0 — Project bootstrap

```text
Task M0만 구현해줘.

목표:
Spring Boot + Spring AI 기반 프로젝트 골격을 만들어줘.

범위:
- Gradle Kotlin DSL
- Java 21
- Spring Boot 4.x
- Spring AI BOM 2.x
- WebFlux
- 기본 application.yml
- 애플리케이션 시작 테스트

하지 말 것:
- AgentRuntime 구현하지 마
- Tool 구현하지 마
- SessionStore 구현하지 마
- DB 붙이지 마

완료 조건:
- ./gradlew test 통과
- README에 최소 실행 방법 추가
- 변경 파일과 테스트 결과 요약
```

---

## Task M1 — ChatClient hello

```text
Task M1만 구현해줘.

목표:
Spring AI ChatClient를 사용해서 가장 작은 /api/chat endpoint를 만들어줘.

범위:
- ChatClient Bean 구성
- POST /api/chat
- ChatRequest, ChatResponse DTO
- API key가 없어도 테스트 가능한 구조

하지 말 것:
- Tool Calling 붙이지 마
- AgentRuntime 만들지 마
- Session 저장하지 마

완료 조건:
- Controller 테스트 또는 smoke test 추가
- ./gradlew test 통과
- README에 GEMINI_API_KEY, OPENAI_API_KEY, 모델 provider 설정 방법 추가
```

---

## Task M2 — Agent domain model

```text
Task M2만 구현해줘.

목표:
AgentRuntime 구현 전에 필요한 도메인 타입을 만들어줘.

범위:
- AgentMessage sealed interface
- UserMessage, AssistantMessage, ToolResultMessage
- AgentEvent sealed interface
- AgentStartedEvent, AgentCompletedEvent, AssistantDeltaEvent, ToolCallStartedEvent, ToolCallFinishedEvent, ToolCallDeniedEvent, AgentErrorEvent
- AgentRunOptions
- AgentPromptCommand
- IdGenerator

하지 말 것:
- ChatClient 연결하지 마
- Tool 구현하지 마
- SessionStore 구현하지 마

완료 조건:
- 타입 생성 테스트
- Jackson 직렬화가 필요하면 테스트 포함
- ./gradlew test 통과
```

---

## Task M3 — JSONL SessionStore

```text
Task M3만 구현해줘.

목표:
Agent 세션, 메시지, 이벤트를 JSONL 파일로 저장하는 SessionStore를 만들어줘.

범위:
- AgentSession
- SessionStore interface
- JsonlSessionStore
- SessionProperties
- StoredMessage, StoredEvent load helper DTO
- SessionStoreException
- messages.jsonl append/load
- events.jsonl append/load
- session.json 저장

하지 말 것:
- DB 붙이지 마
- branch/fork 구현하지 마
- compaction 구현하지 마
- AgentRuntime 구현하지 마
- API/SSE endpoint 붙이지 마
- Tool 구현하지 마
- AgentMessage/AgentEvent sealed interface에 @JsonTypeInfo 기반 다형성 설정을 붙이지 마

완료 조건:
- 세션 생성 테스트
- 메시지 append/load 테스트
- 이벤트 append/load 테스트
- 사람이 읽을 수 있는 JSONL 형태 유지
- `kind`/`type` discriminator로 concrete message/event를 복원
- 알 수 없는 `kind`/`type`은 테스트로 실패 확인
- JSONL 한 줄은 flat concrete object이고 `payload` wrapper가 없음을 테스트로 확인
- `kind`/`type` 누락, 문자열이 아닌 discriminator, malformed JSONL 실패 테스트
- append 순서 보존 테스트
- 없는 session에 대해 `findSession`은 empty, append/load는 명확한 실패 테스트
- sessionId path traversal 차단 테스트
- 손상된 `session.json` 실패 테스트
- ./gradlew test 통과

구현 결정:
- JSONL 한 줄은 concrete message/event JSON object로 둔다.
- load 시 `JsonNode root = objectMapper.readTree(line)`로 먼저 읽는다.
- root object에서 `kind` 또는 `type` discriminator를 검증한다.
- session 패키지 내부 helper DTO가 discriminator와 전체 root `json`을 들고, 명시적 switch로 concrete record로 변환한다.
- concrete record 복원은 `objectMapper.treeToValue(root, UserMessage.class)`처럼 root object를 대상으로 한다.
- flat JSONL line을 `StoredMessage`/`StoredEvent`로 직접 역직렬화하지 마. 파일에는 `json` 필드가 없다.
- `StoredMessage`, `StoredEvent`는 내부 load helper이지 on-disk wrapper가 아니다. `{"kind":"user","payload":{...}}` 형태로 저장하지 마.
- M2 도메인 타입은 저장 방식에 과하게 묶지 않는다.
```

---

## Task M4 — Workspace guard

```text
Task M4만 구현해줘.

목표:
파일 도구가 workspace 밖으로 나가지 못하게 하는 안전 계층을 만들어줘.

범위:
- WorkspaceProperties
- SafePathResolver
- SecretFileGuard
- WorkspaceAccessDeniedException
- path traversal 테스트
- secret file deny 테스트

차단해야 할 예:
- ../outside.txt
- /etc/passwd
- .env
- .env.local
- *.pem
- *.key
- id_rsa
- id_ed25519

완료 조건:
- workspace 내부 파일은 허용
- workspace 밖 경로는 차단
- secret pattern은 차단
- ./gradlew test 통과
```

---

## Task M5 — Read-only tools

```text
Task M5만 구현해줘.

목표:
읽기 전용 built-in tools를 만들어줘.

범위:
- AgentTool
- ToolSpec
- ToolArguments
- ToolResult
- ToolSafetyLevel 또는 read-only 여부를 표현하는 최소 타입
- ToolProperties (`agent.tools.max-tool-output-chars`)
- ToolRegistry
- LsTool
- ReadTool
- FindTool
- GrepTool

도구 규칙:
- 모든 경로는 SafePathResolver를 거쳐야 함
- SecretFileGuard를 적용해야 함
- 출력 길이를 제한해야 함
- .git, build, .gradle은 검색 기본 제외
- read는 UTF-8 텍스트 파일만 허용하고 large/binary file은 실패
- ls/find/grep 결과에서는 secret path를 제외
- grep은 large/binary file을 skip하고 metadata에 skip count를 남김
- find는 glob 기반으로 시작함
- grep은 regex가 아니라 literal substring 검색으로 시작함

하지 말 것:
- write/edit/bash 구현하지 마
- ToolPolicy, ToolPolicyDecision, DefaultToolPolicy 구현하지 마
- ToolExecutionContext, ToolExecutionService 구현하지 마
- Spring AI adapter 구현하지 마

구현 결정:
- M5의 AgentTool은 context 없는 `execute(ToolArguments arguments)` 계약으로 시작해도 됨.
- ToolExecutionContext는 M6에서 공통 실행 정책을 붙일 때 도입한다.
- ToolRegistry는 read-only tool만 다루므로 options 없는 `enabledTools()` 형태로 시작해도 됨.
- `allowWrite`, `allowBash` 기반 tool 활성화는 M6 이후로 미룬다.
- `*.java`는 파일명 기준 매칭, `src/**/*.java`는 workspace 상대 경로 기준 매칭으로 테스트에 고정한다.
- M5 도구 내부 제한과 M6 공통 policy는 별개다. M5에서는 각 도구가 스스로 path/secret/size/output 제한을 지킨다.

완료 조건:
- read/ls/find/grep 테스트
- secret 파일 read 실패 테스트
- output limit 테스트
- large/binary file 처리 테스트
- ls/find/grep에서 secret path 제외 테스트
- ./gradlew test 통과
```

---

## Task M6 — Tool policy and executor

```text
Task M6만 구현해줘.

목표:
Tool 실행 전후 정책과 공통 executor를 만들어줘.

범위:
- ToolPolicy
- ToolPolicyDecision
- DefaultToolPolicy
- ToolExecutionContext
- ToolExecutionService
- AgentRunOptions의 allowWrite/allowBash 반영
- max output truncation

하지 말 것:
- Spring AI adapter 구현하지 마
- write/edit/bash 실제 도구 구현하지 마

완료 조건:
- read-only tool은 기본 허용
- write safety level tool은 allowWrite=false에서 거부되는 테스트
- bash safety level tool은 allowBash=false에서 거부되는 테스트
- output truncation 테스트
- ./gradlew test 통과
```

---

## Task M7 — Spring AI tool adapter

```text
Task M7만 구현해줘.

목표:
내부 AgentTool 구조를 Spring AI Tool Calling에 연결해줘.

범위:
- SpringAiToolCallbackAdapter 또는 equivalent adapter
- ToolRegistry에서 활성 tool 목록을 가져와 Spring AI에 넘기는 구조
- ToolExecutionService를 반드시 경유
- tool start/finish/denied event를 발행할 수 있는 구조

주의:
- Spring AI 2.x의 실제 ToolCallback API를 직접 확인하고 맞춰줘.
- API가 예상과 다르면 가장 단순한 방식으로 구현하고, 설계 차이를 문서에 남겨줘.
- 새 dependency 추가하지 마.

완료 조건:
- 내부 AgentTool 하나가 Spring AI tool로 노출되는 테스트
- ToolExecutionService 경유 테스트
- ./gradlew test 통과
```

---

## Task M8 — AgentRuntime and SSE

```text
Task M8만 구현해줘.

목표:
세션 기반 prompt 처리와 SSE event stream을 연결해줘.

범위:
- AgentRuntime interface
- DefaultAgentRuntime
- AgentController
- POST /api/sessions
- POST /api/sessions/{sessionId}/messages/stream
- SessionStore append
- AgentEvent SSE serialization

동작 예:
사용자가 메시지를 보내면 agent_started, user_message_appended, assistant_delta 또는 assistant_completed, agent_completed 이벤트가 흘러야 함.

하지 말 것:
- write/edit/bash 구현하지 마
- branch/fork 구현하지 마

완료 조건:
- API smoke test
- session JSONL 기록 테스트
- SSE endpoint 테스트
- ./gradlew test 통과
```

---

## Task M9 — Write/Edit tools

```text
Task M9만 구현해줘.

목표:
안전 정책 뒤에 write/edit 도구를 추가해줘.

범위:
- WriteTool
- EditTool
- allowWrite option 연결
- overwrite policy
- exact oldText replace

규칙:
- allowWrite=false면 무조건 거부
- write는 기본적으로 기존 파일 overwrite 금지
- edit는 oldText가 정확히 한 번 매칭될 때만 수정
- secret 파일은 쓰기/수정 금지

완료 조건:
- allowWrite=false 거부 테스트
- allowWrite=true 새 파일 생성 테스트
- overwrite false 테스트
- edit exact match 테스트
- ./gradlew test 통과
```

---

## Task M10 — Bash tool

```text
Task M10만 구현해줘.

목표:
가장 보수적인 bash 도구를 추가해줘.

범위:
- BashTool
- BashCommandPolicy
- allowBash option 연결
- timeout
- output limit
- destructive command denylist

기본 차단 명령 예:
- rm -rf
- git reset --hard
- git clean -fd
- shutdown
- reboot
- curl ... | sh
- wget ... | sh

규칙:
- allowBash=false면 무조건 거부
- working directory는 workspaceRoot로 고정
- timeout 기본 30초
- stdout/stderr 모두 길이 제한

완료 조건:
- allowBash=false 거부 테스트
- 안전한 명령 실행 테스트
- destructive command 거부 테스트
- timeout 테스트
- ./gradlew test 통과
```

---

## Task M11 — README and demo polish

```text
Task M11만 구현해줘.

목표:
포트폴리오용 README와 데모 문서를 정리해줘.

범위:
- README.md 업데이트
- 프로젝트 목적 설명
- 아키텍처 다이어그램 텍스트
- 실행 방법
- API 예시
- 도구 목록
- 안전 정책 설명
- 데모 시나리오
- 후속 확장 방향

하지 말 것:
- 새 기능 구현하지 마
- 코드 구조 변경하지 마

완료 조건:
- README만 보고 프로젝트를 실행할 수 있음
- 블로그 글로 이어질 수 있게 쉬운 설명 포함
- ./gradlew test 통과
```

---

## 작업 완료 후 Codex에게 요청할 요약 형식

```text
작업 완료 후 아래 형식으로 요약해줘.

## 변경 요약
-

## 변경 파일
-

## 테스트
- 실행한 명령:
- 결과:

## SOT와 다른 결정
- 없음 / 있음: ...

## 다음 작업 메모
-
```
