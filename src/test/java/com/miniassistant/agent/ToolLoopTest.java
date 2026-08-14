package com.miniassistant.agent;

import com.miniassistant.llm.ChatMessage;
import com.miniassistant.llm.ChatResponse;
import com.miniassistant.llm.MockLlmClient;
import com.miniassistant.llm.ToolCall;
import com.miniassistant.tools.Tool;
import com.miniassistant.tools.ToolRegistry;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ToolLoopTest {

    @Test
    public void happyPathToolCallThenFinalAnswer() {
        ToolCall call = new ToolCall("call-1", "echo", "{\"text\":\"hi\"}");
        MockLlmClient llm = new MockLlmClient(
                ChatResponse.toolCalls(Collections.singletonList(call)),
                ChatResponse.text("Готово: hi"));
        ToolRegistry registry = new ToolRegistry(Collections.<Tool>singletonList(
                fixedTool("echo", "{\"echoed\":\"hi\"}")));
        ToolLoop loop = new ToolLoop(llm, registry, 5);

        ToolLoopResult result = loop.run(Collections.singletonList(ChatMessage.user("hi")));

        assertTrue(result.isCompleted());
        assertEquals("Готово: hi", result.getFinalAnswer());

        ChatMessage toolResultMessage = lastMessageOfCall(llm, 1);
        assertEquals(ChatMessage.Role.TOOL, toolResultMessage.getRole());
        assertEquals("call-1", toolResultMessage.getToolCallId());
        assertEquals("{\"echoed\":\"hi\"}", toolResultMessage.getContent());
    }

    @Test
    public void stopsAtMaxStepsWithoutExceptionWhenModelNeverFinishes() {
        ChatResponse alwaysToolCall = ChatResponse.toolCalls(
                Collections.singletonList(new ToolCall("call-1", "echo", "{}")));
        MockLlmClient llm = new MockLlmClient(alwaysToolCall, alwaysToolCall, alwaysToolCall);
        ToolRegistry registry = new ToolRegistry(Collections.<Tool>singletonList(fixedTool("echo", "{}")));
        ToolLoop loop = new ToolLoop(llm, registry, 3);

        ToolLoopResult result = loop.run(Collections.singletonList(ChatMessage.user("hi")));

        assertFalse(result.isCompleted());
        assertNull(result.getFinalAnswer());
        assertEquals(3, llm.recordedMessages().size());
    }

    @Test
    public void unknownToolNameProducesErrorResultInsteadOfCrashing() {
        ToolCall unknownCall = new ToolCall("call-1", "does_not_exist", "{}");
        MockLlmClient llm = new MockLlmClient(
                ChatResponse.toolCalls(Collections.singletonList(unknownCall)),
                ChatResponse.text("не смог выполнить"));
        ToolRegistry registry = new ToolRegistry(Collections.<Tool>emptyList());
        ToolLoop loop = new ToolLoop(llm, registry, 5);

        ToolLoopResult result = loop.run(Collections.singletonList(ChatMessage.user("hi")));

        assertTrue(result.isCompleted());
        ChatMessage toolResultMessage = lastMessageOfCall(llm, 1);
        assertEquals(ChatMessage.Role.TOOL, toolResultMessage.getRole());
        assertTrue(toolResultMessage.getContent().contains("error"));
        assertTrue(toolResultMessage.getContent().contains("does_not_exist"));
    }

    @Test
    public void toolExecutionExceptionProducesErrorResultInsteadOfCrashing() {
        ToolCall call = new ToolCall("call-1", "broken", "not-json");
        MockLlmClient llm = new MockLlmClient(
                ChatResponse.toolCalls(Collections.singletonList(call)),
                ChatResponse.text("готово"));
        ToolRegistry registry = new ToolRegistry(Collections.<Tool>singletonList(
                throwingTool("broken", new IllegalArgumentException("invalid json arguments"))));
        ToolLoop loop = new ToolLoop(llm, registry, 5);

        ToolLoopResult result = loop.run(Collections.singletonList(ChatMessage.user("hi")));

        assertTrue(result.isCompleted());
        ChatMessage toolResultMessage = lastMessageOfCall(llm, 1);
        assertEquals(ChatMessage.Role.TOOL, toolResultMessage.getRole());
        assertTrue(toolResultMessage.getContent().contains("error"));
        assertTrue(toolResultMessage.getContent().contains("invalid json arguments"));
    }

    private static ChatMessage lastMessageOfCall(MockLlmClient llm, int callIndex) {
        List<ChatMessage> messages = llm.recordedMessages().get(callIndex);
        return messages.get(messages.size() - 1);
    }

    private static Tool fixedTool(String name, String result) {
        return new FakeTool(name, result, null);
    }

    private static Tool throwingTool(String name, RuntimeException toThrow) {
        return new FakeTool(name, null, toThrow);
    }

    private static final class FakeTool implements Tool {
        private final String name;
        private final String result;
        private final RuntimeException toThrow;

        FakeTool(String name, String result, RuntimeException toThrow) {
            this.name = name;
            this.result = result;
            this.toThrow = toThrow;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "fake tool for ToolLoopTest";
        }

        @Override
        public String jsonSchema() {
            return "{}";
        }

        @Override
        public String execute(String argsJson) {
            if (toThrow != null) {
                throw toThrow;
            }
            return result;
        }
    }
}
