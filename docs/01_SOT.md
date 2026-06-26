# 01. SOT — pi-spring-ai Source of Truth

문서 버전: 0.1
작성 목적: Codex가 이 문서를 기준으로 프로젝트를 일관되게 구현하도록 한다.
프로젝트 성격: 학습용 + 포트폴리오용 + 블로그 시리즈용

---

## 1. 프로젝트 이름

`pi-spring-ai`

정식 설명:

> Spring AI 기반의 작은 코딩 에이전트 하네스. 사용자의 요청을 받아 LLM이 판단하고, 필요한 경우 안전한 도구를 호출하며, 도구 실행 결과를 다시 모델에게 전달하고, 모든 과정을 이벤트와 세션으로 기록한다.

짧은 설명:

> Spring Boot로 만든 작은 Pi-inspired coding agent runtime.

---

## 2. 이 프로젝트가 해결하려는 문제

Java/Spring 개발자는 AI Agent를 배우려고 할 때 Python 또는 TypeScript 중심 예제를 많이 접한다. 하지만 실제 백엔드 개발자는 다음 질문을 하게 된다.

- Spring Boot 애플리케이션 안에서 LLM을 어떻게 호출할 것인가?
- AI가 파일을 읽거나 명령을 실행하게 하려면 어떤 안전장치가 필요한가?
- 단순 챗봇과 tool-use agent의 차이는 무엇인가?
- agent의 작업 과정을 어떻게 로그, 이벤트, 세션으로 남길 것인가?
- Codex 같은 coding agent에게 일을 맡길 때 어떤 작업 단위로 쪼개야 하는가?

이 프로젝트는 위 질문에 대한 작은 기준 구현(reference implementation)을 만드는 것을 목표로 한다.

---

## 3. 핵심 철학

### 3.1 Pi를 복제하지 않는다

Pi Agent Harness의 아이디어는 참고한다.

- tool calling loop
- stateful session
- event stream
- coding tools
- project/workspace safety

하지만 TypeScript/Node 구현을 Java로 그대로 옮기지 않는다. Spring스럽게 재해석한다.

### 3.2 Spring 개발자가 이해할 수 있어야 한다

모든 주요 개념은 Spring 개발자가 익숙한 개념에 대응되어야 한다.

| Agent 개념 | Spring 비유 |
|---|---|
| AgentRuntime | Service orchestrator |
| Tool | `@Service` 메서드 또는 `@Tool` 메서드 |
| ToolPolicy | Spring Security access decision |
| AgentEvent | ApplicationEvent 또는 SSE event |
| SessionStore | Repository |
| ChatClient | WebClient/JdbcTemplate 같은 client abstraction |
| ContextTransformer | HandlerInterceptor/Advisor 같은 전처리 계층 |

### 3.3 너무 과하게 설계하지 않는다

금지한다.

- 처음부터 멀티 모듈 Gradle 구성 금지
- 처음부터 plugin system 금지
- 처음부터 MCP 금지
- 처음부터 Vector DB 금지
- 처음부터 sub-agent 금지
- 처음부터 복잡한 permission UI 금지
- 불필요한 hexagonal architecture 강제 금지
- 모든 것에 interface를 만들지 않기
- 추상화 이름부터 만들고 구현을 나중에 채우지 않기

허용한다.

- 패키지 단위 분리
- 테스트 가능한 작은 interface
- Spring Bean으로 자연스러운 의존성 주입
- 나중에 바꿀 가능성이 높은 모델 호출, 세션 저장, 도구 실행은 얇게 경계 설정

### 3.4 “카파시식” 작업 원칙

이 문서에서 말하는 “카파시식”은 다음처럼 해석한다.

1. 먼저 가장 작은 작동 루프를 만든다.
2. 마법 같은 프레임워크 조합보다 직접 볼 수 있는 단순한 코드를 우선한다.
3. 복잡한 agent 이론보다 입력, 상태, 출력, 실패 사례를 눈으로 확인한다.
4. 코드가 짧아질 때까지 추상화하지 말고, 반복이 실제로 생겼을 때만 추상화한다.
5. 모든 작업 단계는 테스트 또는 실행 로그로 확인한다.
6. 모델이 틀릴 수 있다는 전제로 검증 장치를 둔다.
7. fancy한 데모보다 재현 가능한 작은 데모를 우선한다.

프로젝트에서는 이를 다음 규칙으로 바꾼다.

- 먼저 `read-only agent`를 만든다.
- 그 다음 `write/edit`를 붙인다.
- 마지막에 `bash`를 붙인다.
- 위험한 기능은 기본 비활성화한다.
- 도구 실행 결과는 항상 이벤트와 세션에 남긴다.
- 테스트 없는 tool/policy 코드는 받지 않는다.

---

## 4. MVP 범위

MVP는 다음을 포함한다.

### 4.1 애플리케이션

- 단일 Spring Boot 애플리케이션
- Java 21 기준
- Spring Boot 4.x 계열
- Spring AI 2.x 계열
- Gradle Kotlin DSL 권장
- WebFlux 기반 SSE endpoint

### 4.2 모델 호출

- Spring AI `ChatClient` 사용
- OpenAI 또는 Anthropic 중 하나를 기본 예제로 사용
- 로컬 개발용 Ollama는 선택 기능으로 둔다
- 모델 선택은 설정 파일로 분리한다

### 4.3 도구

MVP 도구 목록:

| 도구 | 기본 활성화 | 설명 |
|---|---:|---|
| `ls` | yes | workspace 내부 디렉터리 목록 |
| `read` | yes | workspace 내부 텍스트 파일 읽기 |
| `find` | yes | 파일 이름/패턴 검색 |
| `grep` | yes | 텍스트 검색 |
| `write` | no | 새 파일 쓰기, 명시적 enable 필요 |
| `edit` | no | 기존 파일 수정, 명시적 enable 필요 |
| `bash` | no | 명령 실행, 명시적 enable + command policy 필요 |

MVP의 기본 모드는 read-only다.

### 4.4 안전 정책

반드시 구현한다.

- workspace root 밖 파일 접근 차단
- path traversal 차단
- binary/large file 읽기 제한
- 출력 길이 제한
- `.env`, secret 파일 읽기 차단
- destructive command 차단
- command timeout
- tool execution audit event

### 4.5 세션

- JSONL 파일 기반 세션 저장
- user/assistant/toolResult/event를 순서대로 기록
- DB는 MVP에서 사용하지 않는다
- parentId 기반 branch는 후속 단계로 남기되, 타입에는 `parentId`를 포함해 확장 가능하게 둔다

### 4.6 API

MVP API:

- `POST /api/sessions`
- `GET /api/sessions/{sessionId}`
- `POST /api/sessions/{sessionId}/messages`
- `GET /api/sessions/{sessionId}/events` 또는 `POST .../stream`

SSE 응답은 `AgentEvent`를 JSON으로 보낸다.

---

## 5. Non-goals

MVP에서 하지 않는다.

### 5.1 Pi 완전 복제

Pi의 TUI, package system, extension system, rich session tree를 그대로 구현하지 않는다.

### 5.2 완성형 IDE/Coding Assistant

VS Code extension, IntelliJ plugin, terminal TUI는 MVP 범위가 아니다.

### 5.3 Multi-agent

Planner, Executor, Reviewer 같은 다중 agent는 MVP 범위가 아니다.

### 5.4 MCP

MCP는 중요한 확장 지점이지만 MVP에서는 제외한다. Tool abstraction이 안정화된 뒤 붙인다.

### 5.5 RAG/Vector DB

프로젝트 파일을 읽고 검색하는 것이 먼저다. Vector indexing은 후속 확장이다.

---

## 6. 성공 기준

MVP가 성공했다고 판단하는 기준은 다음과 같다.

### 6.1 기능 기준

다음 시나리오가 동작해야 한다.

```text
사용자: 이 프로젝트 구조를 분석해줘.
Agent:
  1. ls 도구 호출
  2. README.md 또는 build.gradle.kts 읽기
  3. 핵심 패키지 설명
  4. 실행 과정이 SSE event로 노출
  5. 세션 JSONL 파일에 기록
```

다음 시나리오도 동작해야 한다.

```text
사용자: ../outside.txt를 읽어줘.
Agent:
  1. read 도구 호출 시도
  2. WorkspacePolicy가 차단
  3. 차단 이벤트 기록
  4. 사용자에게 안전상 읽을 수 없다고 설명
```

### 6.2 코드 품질 기준

- 핵심 도메인 타입은 record/sealed interface를 사용한다.
- tool policy는 unit test가 있다.
- session store는 저장/로드 테스트가 있다.
- tool output limit 테스트가 있다.
- API smoke test가 있다.
- 새 production dependency는 반드시 이유를 문서화한다.

### 6.3 포트폴리오 기준

README에서 다음을 설명할 수 있어야 한다.

- AgentRuntime이 무엇인지
- ChatClient와 Tool Calling이 어디에 쓰이는지
- 왜 workspace safety가 중요한지
- 어떻게 이벤트 스트리밍을 제공하는지
- 세션이 어떻게 저장되는지
- 다음 확장 방향이 무엇인지

---

## 7. 기술 선택 기준

### 7.1 기본 기술

| 항목 | 선택 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.x |
| AI | Spring AI 2.x |
| Build | Gradle Kotlin DSL |
| Web | Spring WebFlux |
| Event Streaming | Server-Sent Events |
| Session Store | JSONL file |
| Test | JUnit 5, AssertJ, Spring Boot Test |
| Optional Local Model | Ollama |

### 7.2 왜 WebFlux인가

SSE streaming이 자연스럽다. Agent event는 한 번에 하나의 응답이 아니라 다음처럼 흘러간다.

```text
agent_started
message_received
tool_call_started
tool_call_finished
assistant_delta
assistant_completed
agent_completed
```

따라서 `Flux<AgentEvent>`로 표현하는 것이 자연스럽다.

### 7.3 왜 JSONL인가

DB를 먼저 붙이면 학습 포인트가 흐려진다. Agent session은 append-only log에 가깝다.

```json
{"type":"user_message","text":"프로젝트 분석해줘"}
{"type":"tool_call","tool":"ls","args":{"path":"."}}
{"type":"tool_result","tool":"ls","output":"README.md\nbuild.gradle.kts"}
{"type":"assistant_message","text":"이 프로젝트는..."}
```

이 구조는 디버깅, replay, 블로그 설명에 좋다.

---

## 8. 핵심 도메인 언어

이 프로젝트에서 사용하는 용어는 다음과 같이 고정한다.

| 용어 | 의미 |
|---|---|
| Agent | 사용자 요청을 처리하는 실행 주체 |
| AgentRuntime | 메시지, 모델 호출, 도구 실행, 이벤트 발행을 조율하는 서비스 |
| AgentSession | 하나의 대화/작업 흐름 |
| AgentMessage | user/assistant/toolResult를 포함하는 메시지 |
| AgentEvent | 외부에 관찰 가능한 실행 이벤트 |
| Tool | 모델이 요청할 수 있는 애플리케이션 기능 |
| ToolCall | 모델이 특정 tool을 실행해 달라고 요청한 것 |
| ToolResult | tool 실행 결과 |
| ToolPolicy | tool 실행 전후의 안전 검사 |
| Workspace | agent가 접근 가능한 프로젝트 루트 |
| Context | 모델에게 전달되는 메시지와 시스템 지시 |

---

## 9. 안전 원칙

### 9.1 Fail closed

판단이 애매하면 허용하지 않는다.

예:

- 경로가 workspace 내부인지 확실하지 않다 → deny
- 파일이 텍스트인지 확실하지 않다 → deny or truncated
- 명령이 안전한지 확실하지 않다 → deny
- 출력이 너무 길다 → truncate

### 9.2 Read-first

처음에는 읽기 기능만 제공한다. 쓰기와 명령 실행은 나중에 enable flag 뒤에 붙인다.

### 9.3 Human-visible trace

도구 실행은 숨기지 않는다. 모든 tool call은 이벤트로 남긴다.

### 9.4 Secrets are never normal files

다음은 기본적으로 읽기/쓰기 금지한다.

- `.env`
- `.env.*`
- `*.pem`
- `*.key`
- `id_rsa`
- `id_ed25519`
- `application-prod.yml`
- `application-prod.yaml`
- `secrets.*`

---

## 10. 구현 단계의 우선순위

우선순위는 다음 순서를 따른다.

1. 프로젝트 생성
2. ChatClient hello endpoint
3. Agent domain model
4. JSONL session store
5. Workspace path guard
6. read-only tools
7. ToolCallback adapter
8. AgentRuntime
9. SSE stream
10. write/edit tools
11. bash tool
12. README/demo

---

## 11. Codex 작업 방식

Codex에게 한 번에 “전체 구현”을 맡기지 않는다. 아래 단위로 나눠서 맡긴다.

좋은 작업 요청:

```text
Task 03만 구현해줘. 범위는 AgentMessage, AgentEvent 타입과 JSON 직렬화 테스트까지야. 기존 문서의 SOT를 기준으로 하고, 새 dependency는 추가하지 마.
```

나쁜 작업 요청:

```text
전체 에이전트 완성해줘.
```

Codex 작업 후에는 매번 다음을 확인한다.

- 변경 파일 목록
- 실행한 테스트
- 실패한 테스트가 있는지
- SOT와 다른 결정을 했는지
- 다음 작업에 남길 메모

---

## 12. 변경 관리 규칙

SOT를 변경해야 하는 경우:

- 새 production dependency 추가
- MVP 범위 변경
- 기본 도구 정책 변경
- 세션 저장 방식 변경
- Java/Spring 버전 변경
- 보안 기본값 변경

SOT 변경 없이 가능한 경우:

- 클래스명 소폭 개선
- 테스트 추가
- 문서 오탈자 수정
- 내부 구현 단순화

---

## 13. 최종 README 메시지

README의 핵심 메시지는 다음과 같아야 한다.

```text
pi-spring-ai is a small, Pi-inspired coding agent harness built with Spring AI.
It focuses on a readable tool-use loop, safe workspace tools, event streaming,
and replayable sessions for Java/Spring developers.
```

한국어 버전:

```text
pi-spring-ai는 Spring AI로 만든 작은 코딩 에이전트 하네스입니다.
단순 챗봇이 아니라, LLM이 안전한 도구를 호출하고 그 과정을 이벤트와 세션으로 남기는 구조를 학습하기 위한 프로젝트입니다.
```
