package com.example.pispringai.tool;

import com.example.pispringai.agent.AgentRunOptions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {

    private final List<AgentTool> tools;
    private final Map<String, AgentTool> toolsByName;

    public ToolRegistry(List<AgentTool> tools) {
        this.tools = List.copyOf(tools);
        this.toolsByName = indexTools(this.tools);
    }

    public List<AgentTool> enabledTools() {
        return enabledTools(AgentRunOptions.safeDefaults());
    }

    public List<AgentTool> enabledTools(AgentRunOptions options) {
        Objects.requireNonNull(options, "options must not be null");
        return tools.stream()
                .filter(tool -> isEnabled(tool.spec(), options))
                .toList();
    }

    public AgentTool get(String name) {
        AgentTool tool = toolsByName.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("unknown tool: " + name);
        }
        return tool;
    }

    private static Map<String, AgentTool> indexTools(List<AgentTool> tools) {
        Map<String, AgentTool> indexed = new LinkedHashMap<>();
        for (AgentTool tool : tools) {
            String name = tool.spec().name();
            if (indexed.putIfAbsent(name, tool) != null) {
                throw new IllegalArgumentException("duplicate tool name: " + name);
            }
        }
        return Map.copyOf(indexed);
    }

    private static boolean isEnabled(ToolSpec spec, AgentRunOptions options) {
        return switch (spec.safetyLevel()) {
            case READ_ONLY -> spec.enabledByDefault();
            case WRITE -> options.allowWrite();
            case BASH -> options.allowBash();
        };
    }
}
