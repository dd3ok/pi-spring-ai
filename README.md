# pi-spring-ai

`pi-spring-ai` is a small, Pi-inspired coding agent harness built with Spring AI.
It focuses on a readable tool-use loop, safe workspace tools, event streaming,
and replayable sessions for Java/Spring developers.

한국어로는 Spring AI로 만든 작은 코딩 에이전트 하네스다. 단순 챗봇이 아니라, LLM이 안전한 도구를 호출하고 그 과정을 이벤트와 세션으로 남기는 구조를 학습하기 위한 프로젝트다.

## Repository Baseline

이 저장소의 제품/프로젝트 이름은 `pi-spring-ai`로 둔다. GitHub 저장소 이름은 현재 `spring-pi-agent`지만, 문서와 코드의 애플리케이션 이름은 `pi-spring-ai`를 기준으로 한다.

기준 문서는 다음 파일이다.

- `AGENTS.md`
- `docs/01_SOT.md`
- `docs/02_DESIGN.md`
- `docs/03_IMPLEMENTATION_PLAN.md`
- `docs/04_CODEX_TASK_PROMPTS.md`
- `docs/05_BLOG_SERIES_PLAN.md`

루트의 `01_SOT.md` 같은 중복 문서는 초기 작업 묶음의 사본이므로 추적하지 않는다. 앞으로 수정은 `docs/` 아래 문서에만 한다.

## Working Style

한 번에 하나의 마일스톤만 구현한다. 각 마일스톤은 다음 산출물을 남긴다.

- 작게 동작하는 코드 또는 실행 가능한 데모
- 외부 LLM API 없이 통과하는 테스트
- README 또는 docs의 짧은 변경 기록
- 블로그 글에 넣을 수 있는 의사결정 메모

커밋 단위도 마일스톤에 맞춘다. 예를 들어 `M0 Project bootstrap`, `M1 ChatClient hello`, `M4 Workspace guard`처럼 나눈다.

## MVP Order

1. M0 Project bootstrap
2. M1 ChatClient hello
3. M2 Agent domain model
4. M3 JSONL session store
5. M4 Workspace guard
6. M5 Read-only tools
7. M6 Tool execution policy
8. M7 Spring AI tool adapter
9. M8 AgentRuntime + SSE
10. M9 Write/Edit tools
11. M10 Bash tool
12. M11 README/demo polish

## Current Status

현재는 `M0 Project bootstrap` 단계다. Spring Boot 애플리케이션 골격, Gradle wrapper, 기본 `application.yml`, health endpoint smoke test를 추가했다.

선택한 초기 버전과 의존성은 다음과 같다.

- Java 21 toolchain with `--release 21`
- Spring Boot 4.1.0
- Spring AI BOM 2.0.0
- Spring WebFlux: 이후 SSE endpoint를 위한 웹 기반
- Spring Boot Actuator: M0에서 `/actuator/health`로 애플리케이션 시작 상태를 확인하기 위한 최소 health endpoint

아직 Spring AI model starter는 추가하지 않았다. 실제 `ChatClient` 연결은 다음 단계 `M1 ChatClient hello`에서 붙인다.

## Run

```bash
./gradlew test
```

Windows PowerShell에서는 다음처럼 실행한다.

```powershell
.\gradlew.bat test
```

다음 구현 작업은 `docs/04_CODEX_TASK_PROMPTS.md`의 `Task M1` 프롬프트를 기준으로 시작한다.
