# AGENTS.md — pi-spring-ai Codex 작업 규칙

이 파일은 프로젝트 루트에 둔다. Codex는 작업 전에 반드시 이 파일과 `docs/`의 SOT/설계 문서를 읽어야 한다.

## 1. 작업 기준 문서

작업 전 반드시 읽을 문서:

- `docs/01_SOT.md`
- `docs/02_DESIGN.md`
- `docs/03_IMPLEMENTATION_PLAN.md`
- `docs/04_CODEX_TASK_PROMPTS.md`

블로그 글과 함께 작업할 때 추가로 읽을 문서:

- `docs/05_BLOG_SERIES_PLAN.md`

문서가 없으면 사용자에게 문서 추가를 요청하지 말고, 현재 저장소에 있는 가장 가까운 문서를 찾아 기준으로 삼되, 최종 요약에 누락 사실을 명시한다.

## 2. 핵심 목표

이 프로젝트는 Spring AI 기반의 작은 코딩 에이전트 하네스다.

우선순위:

1. 작게 동작하는 수직 슬라이스
2. 안전한 workspace 도구
3. 테스트 가능한 코드
4. 읽기 쉬운 Spring 구조
5. 포트폴리오에서 설명 가능한 설계

## 3. 과설계 금지

하지 말 것:

- 처음부터 multi-module로 나누지 말 것
- plugin system 만들지 말 것
- MCP를 MVP에 넣지 말 것
- Vector DB/RAG를 MVP에 넣지 말 것
- sub-agent 만들지 말 것
- 아직 필요 없는 interface를 만들지 말 것
- 모든 클래스에 factory/builder를 만들지 말 것
- 한 작업에서 대규모 리팩터링하지 말 것

허용:

- 패키지 단위 분리
- 테스트를 위한 작은 interface
- Spring Bean으로 자연스러운 DI
- 명확한 record/sealed interface

## 4. Java/Spring 규칙

- Java 21 문법을 사용한다.
- record를 적극 사용한다.
- sealed interface는 도메인 이벤트/메시지처럼 닫힌 계층에만 사용한다.
- Lombok은 추가하지 않는다.
- 새 production dependency는 추가 전 이유를 설명한다.
- Spring Boot/Spring AI 버전은 SOT 기준을 따른다.
- WebFlux/SSE는 agent event stream에만 사용한다.
- blocking file IO는 boundedElastic 또는 명확한 service layer에서만 사용한다.

## 5. Tool safety 규칙

파일/명령 도구는 기본적으로 안전해야 한다.

- workspace 밖 접근 금지
- path traversal 금지
- secret 파일 접근 금지
- `.env`, `*.pem`, `*.key`, private key 파일 금지
- output length 제한
- write/edit는 allowWrite=true일 때만
- bash는 allowBash=true일 때만
- bash timeout 필수
- destructive command 차단

판단이 애매하면 허용하지 않는다.

## 6. 테스트 규칙

- 외부 LLM API를 테스트에서 호출하지 않는다.
- API key가 없어도 테스트가 통과해야 한다.
- tool, policy, session store는 unit test가 있어야 한다.
- controller는 최소 smoke test를 둔다.
- 작업 후 가능한 가장 좁은 테스트를 먼저 실행한다.
- 마지막에 `./gradlew test`를 실행한다.

## 7. Git/변경 규칙

- 사용자가 요청하지 않으면 commit하지 않는다.
- 작업 범위 밖 파일을 수정하지 않는다.
- formatting만을 위한 대규모 변경을 하지 않는다.
- 실패한 테스트를 숨기지 않는다.
- 임시 파일은 작업 후 제거한다.

## 8. 문서 규칙

기능을 추가하면 README 또는 docs에 짧게 반영한다.

특히 다음 변경은 문서화한다.

- 새 tool 추가
- 새 policy 추가
- API endpoint 변경
- application.yml 설정 추가
- 보안 기본값 변경
- 새 dependency 추가

## 9. 작업 완료 요약 형식

작업 완료 후 반드시 아래 형식으로 요약한다.

```markdown
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

## 10. 중요한 판단 기준

좋은 구현:

- tool 하나가 클래스 하나와 테스트 하나로 이해된다.
- session JSONL을 사람이 직접 열어도 이해할 수 있다.
- policy 실패가 이벤트와 테스트로 확인된다.
- README에서 흐름이 설명된다.

나쁜 구현:

- 아직 없는 요구사항 때문에 추상화가 많다.
- tool 실행이 어디서 되는지 찾기 어렵다.
- 실패가 로그에만 있고 API/event로 보이지 않는다.
- API key 없이는 테스트가 깨진다.
