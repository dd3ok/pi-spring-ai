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
