# M12. Tool safety는 기능보다 오래 남는다

> 이 글은 `Spring AI로 Pi 스타일 코딩 에이전트 만들기` 시리즈의 일부입니다.
> 목표는 거대한 프레임워크를 한 번에 만드는 것이 아니라, Spring 개발자가 이해할 수 있는 작은 단위로 에이전트 런타임을 쌓아가는 데 있습니다.

## 이번 글의 목적

M11에서 README와 데모를 정리하면서 MVP를 한 번 닫았습니다.

이제부터는 무작정 기능을 늘리지 않습니다.

M12의 목표는 이미 만든 tool safety를 다시 점검하고, 놓치기 쉬운 실패 사례를 테스트로 고정하는 일입니다.

```text
새 tool을 추가하지 않는다.
AgentRuntime loop를 바꾸지 않는다.
Spring AI adapter 구조를 바꾸지 않는다.
MCP를 아직 붙이지 않는다.
새 dependency를 추가하지 않는다.
```

대신 다음 질문에 답합니다.

```text
write/edit은 nested secret path도 막는가?
bash는 PowerShell alias까지 위험 명령으로 보는가?
policy deny reason은 SSE와 JSONL history에 남는가?
기존 output limit과 timeout 테스트는 계속 살아 있는가?
```

M12는 겉으로 보기에는 작은 단계입니다.

하지만 코딩 에이전트에서는 이런 단계가 중요합니다. 기능은 나중에 다시 만들 수 있지만, safety policy가 흔들리면 프로젝트 전체를 믿기 어렵습니다.

## 왜 hardening이 필요한가

M10에서 `bash`를 붙였고, M11에서 README에 safety model을 정리했습니다.

그 시점에도 주요 정책은 이미 있었습니다.

```text
read-only tools는 기본 활성화
write/edit은 allowWrite=true 필요
bash는 allowBash=true 필요
workspace 밖 접근 차단
path traversal 차단
secret file 차단
large/binary file 제한
tool output limit
bash timeout
destructive command deny
```

그렇다면 M12에서 할 일이 없어 보일 수도 있습니다.

하지만 safety 정책은 happy path보다 edge case에서 깨집니다.

예를 들어 `.env`는 막아도 `config/.env.local`은 어떤가요? `id_rsa`는 root에 있을 때만 막히나요, 아니면 `keys/id_rsa`도 막히나요? `Remove-Item -Recurse`는 막아도 `ri -r`은 어떻게 되나요?

이런 질문은 문서로만 답할 수 없습니다.

테스트로 고정해야 합니다.

## M12에서 만들지 않는 것

M12는 새 기능 단계가 아닙니다.

그래서 일부러 하지 않는 것이 많습니다.

```text
MCP client/server 구현
RAG 또는 Vector DB 추가
CLI/TUI/dashboard 추가
LLM-as-Judge 평가 추가
session branch/fork 구현
compaction 구현
multi-agent 구현
plugin system 구현
```

이 항목들은 모두 흥미로운 주제입니다.

하지만 지금 다 넣으면 프로젝트의 중심이 흐려집니다. 이 시리즈의 중심은 Spring AI를 이용해 작은 coding agent runtime을 만드는 일입니다. 그 runtime의 핵심 경계는 `AgentRuntime`, `ToolRegistry`, `ToolExecutionService`, `ToolPolicy`, SSE, JSONL session입니다.

M12는 이 경계를 더 단단하게 만드는 단계입니다.

## safety policy의 위치

이 프로젝트에서 tool 실행은 대략 다음 순서로 흐릅니다.

```text
Model tool call
  -> SpringAiToolCallbackAdapter
  -> ToolExecutionService
  -> ToolPolicy.before(...)
  -> AgentTool.execute(...)
  -> ToolPolicy.after(...)
  -> ToolResult
  -> SSE event
  -> JSONL session history
```

중요한 점은 policy가 tool 실행 바깥에 있다는 것입니다.

`write`, `edit`, `bash` 같은 개별 tool도 자기 책임의 검사를 합니다. 하지만 request 단위에서 `allowWrite`, `allowBash`를 보는 일은 `ToolExecutionService`와 `ToolPolicy`가 맡습니다.

이렇게 나누면 설명이 쉬워집니다.

```text
이 tool은 무엇을 하는가?
이 request에서 실행해도 되는가?
실행 결과는 너무 길지 않은가?
거부되었다면 그 이유가 기록되는가?
```

각 질문을 다른 위치에서 답할 수 있습니다.

M12에서는 이 구조를 바꾸지 않고, 놓친 사례를 테스트로 더합니다.

## nested secret path를 막는다

먼저 write/edit 도구입니다.

M9에서 `write`, `edit`을 추가할 때 secret file 차단은 이미 들어갔습니다.

대표적인 예는 `.env`입니다.

```java
@Test
void rejectsSecretFilePath() {
    ToolResult result = newTool().execute(args(".env", "SECRET=yes", false));

    assertThat(result.error()).isTrue();
    assertThat(result.text()).contains("denied");
    assertThat(Files.exists(workspace.resolve(".env"))).isFalse();
}
```

하지만 실제 repository에서는 secret file이 root에만 있지 않습니다.

```text
config/.env.local
keys/id_rsa
src/main/resources/application-prod.yml
```

그래서 M12에서는 nested path를 직접 테스트합니다.

```java
@Test
void rejectsNestedSecretFilePaths() throws Exception {
    Path config = Files.createDirectories(workspace.resolve("config"));
    Path keys = Files.createDirectories(workspace.resolve("keys"));
    Path resources = Files.createDirectories(workspace.resolve("src/main/resources"));

    ToolResult envLocal = newTool().execute(args("config/.env.local", "SECRET=yes", false));
    ToolResult privateKey = newTool().execute(args("keys/id_rsa", "secret", false));
    ToolResult prodConfig = newTool().execute(args(
            "src/main/resources/application-prod.yml",
            "secret: yes",
            false));

    assertThat(envLocal.error()).isTrue();
    assertThat(privateKey.error()).isTrue();
    assertThat(prodConfig.error()).isTrue();
    assertThat(envLocal.text()).contains("denied");
    assertThat(privateKey.text()).contains("denied");
    assertThat(prodConfig.text()).contains("denied");
    assertThat(Files.exists(config.resolve(".env.local"))).isFalse();
    assertThat(Files.exists(keys.resolve("id_rsa"))).isFalse();
    assertThat(Files.exists(resources.resolve("application-prod.yml"))).isFalse();
}
```

여기서 일부러 부모 디렉터리를 먼저 만듭니다.

부모 디렉터리가 없어서 실패하는 테스트라면 secret guard를 검증한 것이 아닙니다. M12에서 확인하고 싶은 것은 `config`, `keys`, `src/main/resources`가 존재해도 secret file 자체는 만들 수 없다는 점입니다.

`edit`도 같은 관점으로 봅니다.

이미 존재하는 secret file을 수정하려고 해도 원본이 그대로 남아야 합니다.

```java
ToolResult envLocal = tool.execute(args("config/.env.local", "old", "new"));
ToolResult privateKey = tool.execute(args("keys/id_rsa", "old", "new"));
ToolResult prodConfig = tool.execute(args("src/main/resources/application-prod.yml", "old", "new"));

assertThat(envLocal.error()).isTrue();
assertThat(privateKey.error()).isTrue();
assertThat(prodConfig.error()).isTrue();
assertThat(Files.readString(config.resolve(".env.local"))).isEqualTo("old");
assertThat(Files.readString(keys.resolve("id_rsa"))).isEqualTo("old");
assertThat(Files.readString(resources.resolve("application-prod.yml"))).isEqualTo("old");
```

이 테스트는 단순한 보강처럼 보이지만, 나중에 `SecretFileGuard`나 `SafePathResolver`를 손볼 때 중요한 방어선이 됩니다.

## bash policy는 alias까지 봐야 한다

두 번째 보강은 `BashCommandPolicy`입니다.

M10에서는 다음 같은 명령을 차단했습니다.

```text
rm -rf
git reset --hard
git clean -fd
shutdown
reboot
curl ... | sh
wget ... | sh
secret path 참조 command
```

그런데 Windows와 PowerShell을 생각하면 한 가지 구멍이 생깁니다.

PowerShell에는 `Remove-Item`이 있고, 이 명령에는 alias가 많습니다.

```text
Remove-Item -Recurse -Force .
Remove-Item -r -Force .
ri -r .
rd -Recurse .
del -r .
erase -r .
rm -r .
```

`Remove-Item -Recurse`만 보는 정책은 부족합니다.

그래서 먼저 실패하는 테스트를 추가했습니다.

```java
@Test
void deniesPowerShellRemoveItemRecursiveAliases() {
    assertThat(policy.validate("Remove-Item -Recurse -Force .").denied()).isTrue();
    assertThat(policy.validate("Remove-Item -r -Force .").denied()).isTrue();
    assertThat(policy.validate("powershell -NoProfile -Command \"Remove-Item -r -Force .\"").denied()).isTrue();
    assertThat(policy.validate("powershell -NoProfile -Command \"ri -r .\"").denied()).isTrue();
    assertThat(policy.validate("pwsh -NoProfile -Command \"rd -Recurse .\"").denied()).isTrue();
    assertThat(policy.validate("powershell -NoProfile -Command \"del -r .\"").denied()).isTrue();
    assertThat(policy.validate("powershell -NoProfile -Command \"erase -r .\"").denied()).isTrue();
    assertThat(policy.validate("powershell -NoProfile -Command \"rm -r .\"").denied()).isTrue();
}
```

처음에는 이 테스트가 실패했습니다.

그 뒤 production code를 최소로 고쳤습니다.

```java
private static final Pattern POWERSHELL_REMOVE_ITEM_RECURSIVE =
        Pattern.compile("\\bremove-item\\b(?=.*\\s-(?:recurse|r)(?:\\b|[:=]))"
                + "|\\b(?:powershell(?:\\.exe)?|pwsh(?:\\.exe)?)\\b.*"
                + "\\b(?:ri|del|erase|rd|rmdir|rm)\\b(?=.*\\s-(?:recurse|r)(?:\\b|[:=]))");
```

그리고 destructive command 검사에 이 패턴을 넣습니다.

```java
private static boolean isDestructive(String normalized) {
    return RM_RECURSIVE_FORCE.matcher(normalized).find()
            || GIT_RESET_HARD.matcher(normalized).find()
            || GIT_CLEAN_FORCE_DIRECTORY.matcher(normalized).find()
            || REMOTE_SCRIPT_PIPE.matcher(normalized).find()
            || REMOTE_POWERSHELL_PIPE.matcher(normalized).find()
            || SHUTDOWN_OR_REBOOT.matcher(normalized).find()
            || POWERSHELL_REMOVE_ITEM_RECURSIVE.matcher(normalized).find()
            || WINDOWS_DELETE_RECURSIVE.matcher(normalized).find();
}
```

여기서 중요한 점은 새 구조를 만들지 않았다는 것입니다.

정책 엔진을 새로 만들거나, rule DSL을 도입하지 않았습니다. 지금 필요한 것은 하나의 누락된 deny case를 기존 policy 안에 고정하는 일입니다.

M12의 성격에는 이 정도가 맞습니다.

## deny reason은 기록으로 남아야 한다

세 번째 보강은 runtime 기록입니다.

AI agent에서 거부는 조용히 사라지면 안 됩니다.

사용자가 `allowWrite=false` 상태에서 write를 시도했다면, 결과는 세 곳에 남아야 합니다.

```text
모델에 돌려줄 ToolResult
SSE event stream
JSONL session history
```

그래야 나중에 사용자가 이렇게 확인할 수 있습니다.

```text
왜 파일이 안 만들어졌지?
tool이 실행되긴 했나?
정책에서 막힌 건가, tool 내부에서 실패한 건가?
어떤 reason이 남았나?
```

M12에서는 `DefaultAgentRuntimeTest`에 denied event 내용을 더 구체적으로 확인하는 assertion을 추가했습니다.

```java
ToolCallDeniedEvent deniedEvent = store.loadEvents(session.id()).stream()
        .filter(ToolCallDeniedEvent.class::isInstance)
        .map(ToolCallDeniedEvent.class::cast)
        .findFirst()
        .orElseThrow();
assertThat(deniedEvent.callId()).isEqualTo("tc_write");
assertThat(deniedEvent.tool()).isEqualTo("write");
assertThat(deniedEvent.reason()).isEqualTo("Denied: allowWrite=false");
```

이 테스트가 중요한 이유는 단순합니다.

`tool_call_denied`라는 event type이 있다는 사실만으로는 부족합니다. 어떤 tool call이 막혔고, 어떤 tool이었고, 어떤 reason이었는지까지 남아야 디버깅할 수 있습니다.

JSONL session history는 이 프로젝트에서 작은 audit log 역할을 합니다.

## output limit과 timeout은 기존 테스트를 유지한다

M12 문서에서 조심해야 할 부분이 있습니다.

이번 단계에서 output limit과 timeout을 새로 만든 것은 아닙니다.

이미 M6, M10에서 관련 테스트가 있었습니다.

```text
DefaultToolPolicyTest.truncatesToolResultTextAfterExecution
ToolExecutionServiceTest.appliesCommonOutputLimitAfterAllowedToolRuns
BashToolTest.timesOutLongRunningCommand
BashToolTest.returnsPartialOutputWhenTimedOutCommandIsDestroyed
BashToolTest.limitsCommandOutput
```

M12에서 한 일은 이 테스트들이 계속 살아 있는지 확인하고, 새로 발견한 deny edge case를 추가하는 쪽에 가깝습니다.

그래서 README에도 이렇게 적는 편이 정확합니다.

```text
path/secret/bash deny 사례를 더 촘촘히 테스트하고,
기존 output limit 회귀 테스트를 유지한다.
```

문서는 구현보다 앞서가면 안 됩니다.

없는 기능을 있다고 쓰는 것도 문제지만, 이미 있는 테스트를 이번 단계에서 새로 만든 것처럼 쓰는 것도 좋지 않습니다. 포트폴리오 문서는 정확해야 합니다.

## 테스트 흐름

M12에서 테스트는 좁게 시작합니다.

먼저 bash policy regression test를 추가하고 실패를 봅니다.

```text
./gradlew test --tests com.example.pispringai.tool.policy.BashCommandPolicyTest
```

PowerShell alias 케이스가 실패하면, policy가 실제로 그 구멍을 놓치고 있다는 뜻입니다.

그다음 최소 수정으로 green을 만듭니다.

```text
./gradlew test --tests com.example.pispringai.tool.policy.BashCommandPolicyTest
```

write/edit과 runtime event까지 묶어서 좁은 테스트를 다시 돌립니다.

```text
./gradlew test \
  --tests com.example.pispringai.tool.policy.BashCommandPolicyTest \
  --tests com.example.pispringai.tool.builtin.WriteToolTest \
  --tests com.example.pispringai.tool.builtin.EditToolTest \
  --tests com.example.pispringai.agent.DefaultAgentRuntimeTest
```

마지막에는 전체 테스트를 실행합니다.

```text
./gradlew test
```

문서와 whitespace도 확인합니다.

```text
git diff --check
```

이 프로젝트의 기준은 계속 같습니다.

```text
외부 LLM API를 테스트에서 호출하지 않는다.
API key가 없어도 테스트가 통과해야 한다.
```

## 이번 단계의 완료 기준

M12의 완료 기준은 다음입니다.

```text
nested secret path가 write/edit에서 차단된다.
PowerShell recursive delete alias가 bash policy에서 차단된다.
policy denied reason이 tool_call_denied event와 JSONL history에 남는다.
기존 timeout/output limit 테스트가 계속 통과한다.
README가 M12 safety hardening 내용을 과장 없이 설명한다.
./gradlew test가 통과한다.
```

반대로 M12에서 하지 않는 일도 다시 적어 둡니다.

```text
새 built-in tool 추가
AgentRuntime loop 변경
Spring AI adapter 구조 변경
MCP client/server 구현
새 production dependency 추가
대규모 policy refactor
```

이 제한이 있어야 작업이 작아집니다.

작아야 리뷰할 수 있고, 리뷰할 수 있어야 safety를 믿을 수 있습니다.

## Codex에게 줄 작업 프롬프트

```text
Task M12만 수행해줘.

목표:
이미 구현된 read-only/write/edit/bash tool의 safety policy를 테스트와 문서로 더 단단히 고정해줘.

범위:
- path traversal, workspace 밖 접근, secret path deny 사례 확인
- write/edit nested secret path deny 사례 보강
- bash destructive command alias deny 사례 보강
- 기존 timeout/output limit 회귀 테스트 유지 확인
- policy denied가 SSE event와 JSONL history에 남는지 확인
- README safety 설명 보완

하지 말 것:
- 새 tool 추가하지 마
- AgentRuntime loop 변경하지 마
- Spring AI adapter 구조 변경하지 마
- MCP 붙이지 마
- 새 production dependency 추가하지 마
- 대규모 policy refactor 하지 마

완료 조건:
- nested secret path deny가 write/edit 테스트로 고정됨
- PowerShell recursive delete alias deny가 bash policy 테스트로 고정됨
- denied reason이 tool_call_denied event와 JSONL history에 남는지 테스트됨
- README만 보고 M12 safety hardening 내용을 설명할 수 있음
- API key 없이 ./gradlew test 통과
```

## 마무리

M12는 눈에 띄는 기능을 추가한 단계가 아닙니다.

하지만 agent project에서는 이런 작업이 오래 남습니다.

모델은 예측할 수 없는 방식으로 tool을 호출할 수 있습니다. 사용자는 의도하지 않은 경로를 요청할 수 있고, shell command는 OS마다 다른 별칭과 문법을 가집니다.

그래서 safety policy는 한 번 작성하고 끝낼 수 없습니다.

테스트로 계속 고정해야 합니다.

M12에서 확인한 것은 세 가지입니다.

```text
secret은 nested path에서도 secret이다.
위험한 bash 명령은 alias로 불러도 위험하다.
거부된 tool call은 사람이 다시 볼 수 있는 기록으로 남아야 한다.
```

다음 단계인 M13에서는 이 기록 자체를 더 잘 다룹니다.

SSE는 실시간 관찰을 위한 흐름이고, JSONL은 나중에 다시 열어볼 수 있는 기록입니다. M13에서는 session history와 replay 관점에서 이 둘을 더 설명 가능한 형태로 정리합니다.
