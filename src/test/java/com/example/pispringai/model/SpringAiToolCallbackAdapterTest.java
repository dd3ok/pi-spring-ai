package com.example.pispringai.model;

import com.example.pispringai.agent.AgentRunOptions;
import com.example.pispringai.mcp.McpToolBridge;
import com.example.pispringai.tool.AgentTool;
import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolExecutionContext;
import com.example.pispringai.tool.ToolExecutionService;
import com.example.pispringai.tool.ToolRegistry;
import com.example.pispringai.tool.ToolResult;
import com.example.pispringai.tool.ToolSafetyLevel;
import com.example.pispringai.tool.ToolSpec;
import com.example.pispringai.tool.policy.ToolPolicy;
import com.example.pispringai.tool.policy.ToolPolicyDecision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SpringAiToolCallbackAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesAgentToolAsSpringAiToolDefinition() throws Exception {
        AgentTool tool = new StubTool(
                "read",
                "Read a workspace file",
                Map.of("path", "string"),
                ToolSafetyLevel.READ_ONLY,
                true);
        RecordingToolExecutionService executionService = new RecordingToolExecutionService(ToolResult.ok("unused"));

        ToolCallback callback = new SpringAiToolCallbackAdapter(
                tool,
                executionService,
                AgentRunOptions.safeDefaults(),
                objectMapper,
                SpringAiToolCallbackEvents.noop());

        ToolDefinition definition = callback.getToolDefinition();
        JsonNode schema = objectMapper.readTree(definition.inputSchema());

        assertThat(definition.name()).isEqualTo("read");
        assertThat(definition.description()).isEqualTo("Read a workspace file");
        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("properties").path("path").path("type").asText()).isEqualTo("string");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    }

    @Test
    void preservesProvidedMcpToolDefinitionWithoutRebuildingItsSchema() throws Exception {
        AgentTool tool = new StubTool("mcp_demo_search", ToolSafetyLevel.MCP, false);
        ToolDefinition providedDefinition = ToolDefinition.builder()
                .name("mcp_demo_search")
                .description("Search through an MCP server")
                .inputSchema("""
                        {"type":"object","properties":{"filters":{"type":"array","items":{"type":"string"}}},"required":["filters"]}
                        """)
                .build();
        ToolCallback callback = new SpringAiToolCallbackAdapter(
                tool,
                new RecordingToolExecutionService(ToolResult.ok("unused")),
                new AgentRunOptions(false, false, true, 6, 12, Duration.ofSeconds(120)),
                null,
                objectMapper,
                SpringAiToolCallbackEvents.noop(),
                providedDefinition);

        assertThat(callback.getToolDefinition()).isSameAs(providedDefinition);
        JsonNode schema = objectMapper.readTree(callback.getToolDefinition().inputSchema());
        assertThat(schema.path("required").get(0).asText()).isEqualTo("filters");
        assertThat(schema.path("properties").path("filters").path("items").path("type").asText())
                .isEqualTo("string");
    }

    @Test
    void executesToolThroughToolExecutionServiceAndReturnsJsonResult() throws Exception {
        AgentTool tool = new StubTool("ls");
        ToolResult serviceResult = ToolResult.ok("README.md", Map.of("count", 1));
        RecordingToolExecutionService executionService = new RecordingToolExecutionService(serviceResult);
        RecordingCallbackEvents events = new RecordingCallbackEvents();
        AgentRunOptions options = new AgentRunOptions(false, false, 3, 4, Duration.ofSeconds(30));
        ToolCallback callback = new SpringAiToolCallbackAdapter(tool, executionService, options, objectMapper, events);

        String rawResult = callback.call("{\"path\":\".\"}");

        JsonNode result = objectMapper.readTree(rawResult);
        assertThat(result.path("text").asText()).isEqualTo("README.md");
        assertThat(result.path("error").asBoolean()).isFalse();
        assertThat(result.path("metadata").path("count").asInt()).isEqualTo(1);
        assertThat(executionService.executionCount()).isEqualTo(1);
        assertThat(executionService.tool()).isSameAs(tool);
        assertThat(executionService.arguments().values()).containsEntry("path", ".");
        assertThat(executionService.context().options()).isEqualTo(options);
        assertThat(events.startedCount()).isEqualTo(1);
        assertThat(events.finishedCount()).isEqualTo(1);
        assertThat(events.finishedCallId()).isEqualTo(events.startedCallId());
        assertThat(events.finishedCallId()).startsWith("tc_");
        assertThat(events.deniedCount()).isZero();
    }

    @Test
    void passesWorkspaceRootIntoToolExecutionContext() {
        AgentTool tool = new StubTool("read");
        RecordingToolExecutionService executionService = new RecordingToolExecutionService(ToolResult.ok("ok"));
        Path workspaceRoot = Path.of("build/session-workspace");
        ToolCallback callback = new SpringAiToolCallbackAdapter(
                tool,
                executionService,
                AgentRunOptions.safeDefaults(),
                workspaceRoot,
                objectMapper,
                SpringAiToolCallbackEvents.noop());

        callback.call("{\"path\":\"README.md\"}");

        assertThat(executionService.context().workspaceRoot()).isEqualTo(workspaceRoot.toAbsolutePath().normalize());
    }

    @Test
    void returnsPolicyDeniedErrorResultToModelWithoutFinishedEvent() throws Exception {
        AgentTool tool = new StubTool("write", ToolSafetyLevel.WRITE, false);
        ToolResult deniedResult = ToolResult.error("Denied: allowWrite=false", Map.of("policyDenied", true));
        RecordingToolExecutionService executionService = new RecordingToolExecutionService(deniedResult);
        RecordingCallbackEvents events = new RecordingCallbackEvents();
        ToolCallback callback = new SpringAiToolCallbackAdapter(
                tool,
                executionService,
                AgentRunOptions.safeDefaults(),
                objectMapper,
                events);

        String rawResult = callback.call("{\"path\":\"demo.md\"}");

        JsonNode result = objectMapper.readTree(rawResult);
        assertThat(result.path("error").asBoolean()).isTrue();
        assertThat(result.path("text").asText()).contains("allowWrite");
        assertThat(result.path("metadata").path("policyDenied").asBoolean()).isTrue();
        assertThat(events.startedCount()).isEqualTo(1);
        assertThat(events.deniedCount()).isEqualTo(1);
        assertThat(events.finishedCount()).isZero();
    }

    @Test
    void nullExecutionResultReturnsErrorResultToModel() throws Exception {
        AgentTool tool = new StubTool("read");
        RecordingToolExecutionService executionService = new RecordingToolExecutionService(null);
        RecordingCallbackEvents events = new RecordingCallbackEvents();
        ToolCallback callback = new SpringAiToolCallbackAdapter(
                tool,
                executionService,
                AgentRunOptions.safeDefaults(),
                objectMapper,
                events);

        String rawResult = callback.call("{\"path\":\"README.md\"}");

        JsonNode result = objectMapper.readTree(rawResult);
        assertThat(result.path("error").asBoolean()).isTrue();
        assertThat(result.path("text").asText()).contains("no result");
        assertThat(events.startedCount()).isEqualTo(1);
        assertThat(events.finishedCount()).isEqualTo(1);
        assertThat(events.deniedCount()).isZero();
        assertThat(events.finishedResult().error()).isTrue();
    }

    @Test
    void malformedArgumentsReturnErrorResultWithoutExecutingTool() throws Exception {
        AgentTool tool = new StubTool("read");
        RecordingToolExecutionService executionService = new RecordingToolExecutionService(ToolResult.ok("unused"));
        RecordingCallbackEvents events = new RecordingCallbackEvents();
        ToolCallback callback = new SpringAiToolCallbackAdapter(
                tool,
                executionService,
                AgentRunOptions.safeDefaults(),
                objectMapper,
                events);

        String rawResult = callback.call("not-json");

        JsonNode result = objectMapper.readTree(rawResult);
        assertThat(result.path("error").asBoolean()).isTrue();
        assertThat(result.path("text").asText()).contains("invalid tool arguments");
        assertThat(executionService.executionCount()).isZero();
        assertThat(events.startedCount()).isZero();
        assertThat(events.finishedCount()).isZero();
        assertThat(events.deniedCount()).isZero();
        assertThat(events.failedCount()).isEqualTo(1);
        assertThat(events.failedCallId()).startsWith("tc_");
        assertThat(events.failedResult().error()).isTrue();
    }

    @Test
    void rejectsAgentToolWithoutSpec() {
        AgentTool tool = new NullSpecTool();
        RecordingToolExecutionService executionService = new RecordingToolExecutionService(ToolResult.ok("unused"));

        assertThatThrownBy(() -> new SpringAiToolCallbackAdapter(
                tool,
                executionService,
                AgentRunOptions.safeDefaults(),
                objectMapper,
                SpringAiToolCallbackEvents.noop()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tool spec");
    }

    @Test
    void factoryCreatesCallbacksForEnabledToolsOnly() {
        AgentTool read = new StubTool("read", ToolSafetyLevel.READ_ONLY, true);
        AgentTool write = new StubTool("write", ToolSafetyLevel.WRITE, false);
        AgentTool bash = new StubTool("bash", ToolSafetyLevel.BASH, false);
        ToolRegistry registry = new ToolRegistry(List.of(read, write, bash));
        RecordingToolExecutionService executionService = new RecordingToolExecutionService(ToolResult.ok("unused"));
        McpToolBridge mcpToolBridge = mock(McpToolBridge.class);
        SpringAiToolCallbackFactory factory = new SpringAiToolCallbackFactory(
                registry,
                executionService,
                objectMapper,
                SpringAiToolCallbackEvents.noop(),
                mcpToolBridge);

        List<ToolCallback> callbacks = factory.callbacksFor(new AgentRunOptions(
                true,
                false,
                6,
                12,
                Duration.ofSeconds(120)));

        assertThat(callbacks)
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("read", "write");
        assertThat(factory.providerFor(AgentRunOptions.safeDefaults()).getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("read");
        verifyNoInteractions(mcpToolBridge);
    }

    @Test
    void factoryFallsBackToNoopEventsWhenProviderIsNull() {
        AgentTool read = new StubTool("read");
        ToolRegistry registry = new ToolRegistry(List.of(read));
        RecordingToolExecutionService executionService = new RecordingToolExecutionService(ToolResult.ok("ok"));
        SpringAiToolCallbackFactory factory = new SpringAiToolCallbackFactory(
                registry,
                executionService,
                objectMapper,
                (ObjectProvider<SpringAiToolCallbackEvents>) null,
                mock(McpToolBridge.class));

        assertThat(factory.callbacksFor(AgentRunOptions.safeDefaults()))
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("read");
    }

    private record StubTool(
            String name,
            String description,
            Map<String, Object> inputSchema,
            ToolSafetyLevel safetyLevel,
            boolean enabledByDefault
    ) implements AgentTool {

        private StubTool(String name) {
            this(name, ToolSafetyLevel.READ_ONLY, true);
        }

        private StubTool(String name, ToolSafetyLevel safetyLevel, boolean enabledByDefault) {
            this(name, "stub tool", Map.of(), safetyLevel, enabledByDefault);
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec(name, description, inputSchema, safetyLevel, enabledByDefault);
        }

        @Override
        public ToolResult execute(ToolArguments arguments) {
            return ToolResult.ok("direct execution should not be used in this test");
        }
    }

    private static final class NullSpecTool implements AgentTool {

        @Override
        public ToolSpec spec() {
            return null;
        }

        @Override
        public ToolResult execute(ToolArguments arguments) {
            return ToolResult.ok("unused");
        }
    }

    private static final class RecordingToolExecutionService extends ToolExecutionService {

        private final ToolResult result;
        private AgentTool tool;
        private ToolArguments arguments;
        private ToolExecutionContext context;
        private int executionCount;

        private RecordingToolExecutionService(ToolResult result) {
            super(new AllowingToolPolicy());
            this.result = result;
        }

        @Override
        public ToolResult execute(AgentTool tool, ToolArguments arguments, ToolExecutionContext context) {
            this.tool = tool;
            this.arguments = arguments;
            this.context = context;
            executionCount++;
            return result;
        }

        private AgentTool tool() {
            return tool;
        }

        private ToolArguments arguments() {
            return arguments;
        }

        private ToolExecutionContext context() {
            return context;
        }

        private int executionCount() {
            return executionCount;
        }
    }

    private static final class RecordingCallbackEvents implements SpringAiToolCallbackEvents {

        private int startedCount;
        private int finishedCount;
        private int deniedCount;
        private int failedCount;
        private String startedCallId;
        private String finishedCallId;
        private String failedCallId;
        private ToolResult finishedResult;
        private ToolResult failedResult;

        @Override
        public void toolCallStarted(String toolName, ToolArguments arguments) {
            startedCount++;
        }

        @Override
        public void toolCallStarted(String toolCallId, String toolName, ToolArguments arguments) {
            startedCallId = toolCallId;
            toolCallStarted(toolName, arguments);
        }

        @Override
        public void toolCallFinished(String toolName, ToolResult result) {
            finishedCount++;
            finishedResult = result;
        }

        @Override
        public void toolCallFinished(String toolCallId, String toolName, ToolResult result) {
            finishedCallId = toolCallId;
            toolCallFinished(toolName, result);
        }

        @Override
        public void toolCallDenied(String toolName, ToolResult result) {
            deniedCount++;
        }

        @Override
        public void toolCallDenied(String toolCallId, String toolName, ToolResult result) {
            toolCallDenied(toolName, result);
        }

        @Override
        public void toolCallFailed(String toolName, ToolResult result) {
            failedCount++;
            failedResult = result;
        }

        @Override
        public void toolCallFailed(String toolCallId, String toolName, ToolResult result) {
            failedCallId = toolCallId;
            toolCallFailed(toolName, result);
        }

        private int startedCount() {
            return startedCount;
        }

        private int finishedCount() {
            return finishedCount;
        }

        private int deniedCount() {
            return deniedCount;
        }

        private int failedCount() {
            return failedCount;
        }

        private ToolResult finishedResult() {
            return finishedResult;
        }

        private String startedCallId() {
            return startedCallId;
        }

        private String finishedCallId() {
            return finishedCallId;
        }

        private String failedCallId() {
            return failedCallId;
        }

        private ToolResult failedResult() {
            return failedResult;
        }
    }

    private static final class AllowingToolPolicy implements ToolPolicy {

        @Override
        public ToolPolicyDecision before(AgentTool tool, ToolArguments arguments, ToolExecutionContext context) {
            return ToolPolicyDecision.allow();
        }

        @Override
        public ToolResult after(AgentTool tool, ToolArguments arguments, ToolResult result, ToolExecutionContext context) {
            return result;
        }
    }
}
