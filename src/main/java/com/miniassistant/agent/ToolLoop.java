package com.miniassistant.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniassistant.llm.ChatMessage;
import com.miniassistant.llm.ChatResponse;
import com.miniassistant.llm.LlmClient;
import com.miniassistant.llm.ToolCall;
import com.miniassistant.llm.ToolSpec;
import com.miniassistant.tools.Tool;
import com.miniassistant.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Цикл tool-calling: прогоняет историю сообщений через {@link LlmClient},
 * пока модель не даст финальный текстовый ответ или не будет исчерпан
 * {@code maxSteps} - что бы ни случилось, наружу не бросает исключение
 * (неизвестное имя инструмента и ошибка внутри {@link Tool#execute} уходят
 * обратно модели как {@code role=tool} результат с {@code {"error": "..."}}).
 */
public final class ToolLoop {

    private final LlmClient llmClient;
    private final ToolRegistry registry;
    private final int maxSteps;
    private final ObjectMapper mapper = new ObjectMapper();

    public ToolLoop(LlmClient llmClient, ToolRegistry registry, int maxSteps) {
        this.llmClient = llmClient;
        this.registry = registry;
        this.maxSteps = maxSteps;
    }

    public ToolLoopResult run(List<ChatMessage> initialMessages) {
        List<ChatMessage> messages = new ArrayList<>(initialMessages);
        List<ToolSpec> toolSpecs = toolSpecs();
        List<String> calledToolNames = new ArrayList<>();

        for (int step = 0; step < maxSteps; step++) {
            ChatResponse response = llmClient.chat(messages, toolSpecs);

            if (!response.hasToolCalls()) {
                return ToolLoopResult.finalAnswer(response.getContent(), calledToolNames);
            }

            messages.add(ChatMessage.assistantToolCalls(response.getToolCalls()));
            for (ToolCall call : response.getToolCalls()) {
                calledToolNames.add(call.getName());
                String resultJson = executeSafely(call);
                messages.add(ChatMessage.toolResult(call.getId(), resultJson));
            }
        }

        return ToolLoopResult.stepLimitReached(calledToolNames);
    }

    private List<ToolSpec> toolSpecs() {
        List<ToolSpec> specs = new ArrayList<>();
        for (Tool tool : registry.all()) {
            specs.add(new ToolSpec(tool.name(), tool.description(), tool.jsonSchema()));
        }
        return specs;
    }

    private String executeSafely(ToolCall call) {
        Tool tool = registry.find(call.getName());
        if (tool == null) {
            return errorJson("unknown tool: " + call.getName());
        }
        try {
            return tool.execute(call.getArgumentsJson());
        } catch (RuntimeException e) {
            return errorJson("tool '" + call.getName() + "' failed: " + e.getMessage());
        }
    }

    private String errorJson(String message) {
        try {
            return mapper.writeValueAsString(Collections.singletonMap("error", message));
        } catch (JsonProcessingException e) {
            return "{\"error\":\"internal error while formatting tool error\"}";
        }
    }
}
