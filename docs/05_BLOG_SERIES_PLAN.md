# 05. Blog Series Plan — pi-spring-ai 작업 기록 계획

이 문서는 구현 작업과 블로그 글을 같은 리듬으로 남기기 위한 계획이다.

## 원칙

- 한 글은 한 마일스톤만 다룬다.
- 구현보다 의사결정을 먼저 설명한다.
- 데모, 테스트, 실패 사례를 함께 남긴다.
- 위험한 기능은 구현보다 안전 정책을 먼저 설명한다.
- 글에서 사용한 코드 상태는 커밋 또는 태그로 다시 찾을 수 있게 한다.

## 시리즈 목차

| 글 | 구현 단계 | 글의 초점 | 완료 증거 |
|---|---|---|---|
| 0 | M-1 Documentation baseline | 왜 작은 Spring AI agent harness를 만들고, 어떤 기준 문서를 먼저 두는가 | README와 docs가 추적됨 |
| 1 | M0 Project bootstrap | Spring Boot 4.x + Spring AI 2.x 골격을 어떻게 시작할 것인가 | `./gradlew test` 통과 |
| 2 | M1 ChatClient hello | 가장 작은 LLM 호출 endpoint와 테스트 가능한 구조 | `/api/chat` smoke test |
| 3 | M2 Agent domain model | agent를 메시지와 이벤트 타입으로 먼저 모델링하는 이유 | 도메인 타입 테스트 |
| 4 | M3 JSONL session store | DB 없이 replay 가능한 session log를 만드는 이유 | JSONL append/load 테스트 |
| 5 | M4 Workspace guard | AI가 파일을 읽기 전에 필요한 path/secret 안전장치 | traversal/secret 차단 테스트 |
| 6 | M5 Read-only tools | `ls`, `read`, `find`, `grep`을 작게 붙이는 방식 | tool unit test |
| 7 | M6 Tool execution policy | 도구 실행 전후 정책을 한 곳에서 검증하는 구조 | allow/deny/truncate 테스트 |
| 8 | M7 Spring AI tool adapter | 내부 tool 모델과 Spring AI adapter를 분리하는 이유 | adapter 테스트 |
| 9 | M8 AgentRuntime + SSE | 사용자 요청에서 이벤트 스트림까지 이어지는 첫 agent loop | SSE/API smoke test |
| 10 | M9 Write/Edit tools | 쓰기 도구를 기본 비활성화로 붙이는 방식 | allowWrite 테스트 |
| 11 | M10 Bash tool | 명령 실행을 가장 보수적으로 허용하는 방식 | destructive command 차단 테스트 |
| 12 | M11 README/demo polish | 포트폴리오로 설명 가능한 최종 데모 정리 | README만 보고 실행 가능 |

## 각 글의 고정 구성

1. 이번 단계에서 만들 것
2. 만들지 않을 것
3. 설계 결정과 이유
4. 핵심 코드 흐름
5. 테스트와 실패 사례
6. 다음 단계로 넘길 메모

## 커밋/태그 메모

각 글을 발행할 때 해당 커밋 해시를 글 하단에 남긴다. 필요하면 나중에 `blog/m0`, `blog/m1` 같은 태그를 붙인다.

## 구현 메모

### M0 Project bootstrap

- 왜 Spring Boot 4.x와 Spring AI 2.x를 기준으로 잡는가?
- 왜 처음부터 agent runtime을 만들지 않는가?
- API key 없이도 테스트가 통과해야 하는 이유는 무엇인가?

M0 구현 메모:

- Spring Boot는 `4.1.0`, Spring AI BOM은 `2.0.0`으로 고정한다.
- WebFlux는 이후 SSE event stream을 위해 먼저 둔다.
- Actuator는 `/actuator/health` smoke test를 위해 둔다.
- 실제 model starter는 M1에서 추가한다. 기본 예제는 Gemini Google GenAI로 두고, OpenAI starter는 provider 전환 예제로 유지한다.

### M1 ChatClient hello

핵심 질문은 다음과 같다.

- 왜 agent runtime보다 `/api/chat`을 먼저 붙이는가?
- 왜 controller test에서 fake service를 쓰는가?
- 왜 model starter는 M1에서야 추가하는가?

M1 구현 메모:

- Spring AI Google GenAI starter는 Gemini를 기본 provider로 쓰기 위해 추가한다.
- Spring AI OpenAI starter는 같은 `/api/chat` 코드가 provider 설정만 바꿔도 확장되는 예제로 유지한다.
- `/api/chat`은 아직 session, tool, event stream과 연결하지 않는다.
- 외부 LLM API는 테스트에서 호출하지 않는다.
- 실제 실행에는 `GEMINI_API_KEY` 또는 `OPENAI_API_KEY`가 필요하지만, 테스트는 dummy key와 fake service만 사용한다.
- 다음 글은 `M2 Agent domain model`이다.

M1.1 provider 변경 메모:

- Gemini API는 무료 tier가 있어 학습/블로그 독자가 따라 해 보기 쉽다.
- `spring.ai.model.chat`을 `${AGENT_MODEL_PROVIDER:google-genai}`로 두어 기본값은 Gemini, 전환값은 OpenAI가 되게 한다.
- 테스트에서는 Google GenAI auto-configuration을 통과시키기 위한 dummy key만 사용하고, 실제 외부 API 호출은 하지 않는다.

### M2 Agent domain model

핵심 질문은 다음과 같다.

- 왜 에이전트를 "기록 가능한 상태 전이"로 보는가?
- 왜 Message와 Event를 분리하는가?
- 왜 실제 tool/runtime/session 구현 전에 타입 계약을 먼저 잡는가?

M2 구현 메모:

- `AgentMessage`와 `AgentEvent`는 Java 21 sealed interface와 record로 둔다.
- message 본문 필드는 `text`로 통일한다. `messageId`는 message record를 가리키는 ID다.
- JSON discriminator는 message의 `kind`, event의 `type`으로 둔다.
- `AgentRunOptions.safeDefaults()`는 read-only 기본값을 표현한다.
- JSON 테스트는 문자열 포함 검사가 아니라 `JsonNode` field set과 값을 확인한다.
- sealed interface 다형성 역직렬화는 M2에서 붙이지 않는다.
- 다음 글은 `M3 JSONL session store`다.

### M3 JSONL session store

핵심 질문은 다음과 같다.

- 왜 MVP에서 DB가 아니라 JSONL append-only log를 쓰는가?
- 왜 Jackson `@JsonTypeInfo` 대신 root JSON helper DTO와 명시적 switch 매핑을 쓰는가?
- 사람이 열어볼 수 있는 session log를 어떻게 테스트로 보장하는가?

M3 구현 메모:

- `AgentSession`과 `SessionStore`를 추가한다.
- `JsonlSessionStore`는 `session.json`, `messages.jsonl`, `events.jsonl`을 관리한다.
- 저장되는 JSONL 한 줄은 concrete message/event JSON object로 둔다.
- load 시 `StoredMessage(kind, json)`, `StoredEvent(type, json)` 같은 내부 helper DTO를 사용한다.
- load 순서는 `readTree(line)` -> discriminator 추출 -> helper DTO 생성 -> switch -> `treeToValue(root, ConcreteRecord.class)`로 설명한다.
- flat JSONL line을 helper DTO로 직접 역직렬화하지 않는다. 파일에는 `json` 필드가 없다.
- 파일에는 `payload` wrapper를 쓰지 않고 M2의 flat concrete JSON object를 그대로 저장한다.
- 알 수 없는 `kind`/`type`은 조용히 무시하지 말고 실패시킨다.
- `kind`/`type` 누락, 문자열이 아닌 discriminator, malformed JSONL, 손상된 `session.json`, sessionId path traversal도 테스트로 고정한다.
- M3에서는 API, SSE, AgentRuntime, Tool을 연결하지 않는다.

### M5 Read-only tools

핵심 질문은 다음과 같다.

- 왜 `ToolPolicy`보다 `ls`, `read`, `find`, `grep` 도구를 먼저 작게 만드는가?
- 왜 M5 도구도 `SafePathResolver`와 `SecretFileGuard`를 직접 통과해야 하는가?
- 왜 `grep`은 regex가 아니라 literal substring 검색으로 시작하는가?

M5 구현 메모:

- M5는 도구 모델과 읽기 전용 built-in tool을 만드는 단계다.
- `ToolPolicy`, `ToolExecutionService`, `ToolExecutionContext`는 M6로 남긴다.
- `AgentTool`은 context 없는 작은 `execute(ToolArguments)` 계약으로 시작해도 된다.
- 각 도구는 공통 policy가 생기기 전에도 스스로 안전해야 한다.
- 모든 경로는 `SafePathResolver.resolveExisting()`을 통과한다.
- `SecretFileGuard`는 read 차단뿐 아니라 `ls`, `find`, `grep` 결과 필터링에도 적용한다.
- `ReadTool`은 UTF-8 텍스트 파일만 읽고 large/binary file은 실패시킨다.
- `FindTool`은 glob 기반으로 시작한다.
- `GrepTool`은 literal substring 검색으로 시작하고 large/binary file은 skip한다.
- M5의 output limit은 도구 내부 안전장치이고, M6의 output truncation은 공통 실행 정책으로 한 번 더 적용한다.
