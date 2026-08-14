package com.miniassistant.llm;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Проверяет форму запроса/ответа {@link HttpLlmClient} против HTTP-стаба
 * (MockWebServer из okhttp, test-scope) - реального сетевого вызова к
 * настоящему LLM-провайдеру здесь нет, только протокол Chat Completions.
 */
public class HttpLlmClientTest {

    private MockWebServer server;

    @Before
    public void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    public void sendsModelMessagesAndToolsInRequestBodyWithAuthHeader() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hi\"}}]}"));

        HttpLlmClient client = new HttpLlmClient(
                server.url("/v1/chat/completions").toString(), "test-key", "gpt-test", 5000);

        List<ChatMessage> messages = Arrays.asList(
                ChatMessage.system("system prompt"),
                ChatMessage.user("hello"));
        List<ToolSpec> tools = Collections.singletonList(
                new ToolSpec("current_datetime", "returns current time", "{\"type\":\"object\",\"properties\":{}}"));

        client.chat(messages, tools);

        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("POST", recorded.getMethod());
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"));

        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"model\":\"gpt-test\""));
        assertTrue(body.contains("\"role\":\"system\""));
        assertTrue(body.contains("system prompt"));
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("current_datetime"));
        assertTrue(body.contains("returns current time"));
    }

    @Test
    public void serializesAssistantToolCallHistoryAndToolResultMessages() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"done\"}}]}"));

        HttpLlmClient client = new HttpLlmClient(
                server.url("/v1/chat/completions").toString(), "test-key", "gpt-test", 5000);

        List<ChatMessage> messages = Arrays.asList(
                ChatMessage.user("напомни купить молоко"),
                ChatMessage.assistantToolCalls(Collections.singletonList(
                        new ToolCall("call-1", "add_reminder", "{\"text\":\"молоко\"}"))),
                ChatMessage.toolResult("call-1", "{\"status\":\"ok\"}"));

        client.chat(messages, Collections.<ToolSpec>emptyList());

        String body = server.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8();
        assertTrue(body.contains("\"tool_calls\""));
        assertTrue(body.contains("\"id\":\"call-1\""));
        assertTrue(body.contains("\"name\":\"add_reminder\""));
        assertTrue(body.contains("\"tool_call_id\":\"call-1\""));
        assertTrue(body.contains("\"role\":\"tool\""));
    }

    @Test
    public void parsesFinalTextAnswerWhenResponseHasNoToolCalls() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Сегодня среда.\"}}]}"));

        HttpLlmClient client = new HttpLlmClient(
                server.url("/").toString(), "test-key", "gpt-test", 5000);

        ChatResponse response = client.chat(
                Collections.singletonList(ChatMessage.user("какой сегодня день")),
                Collections.<ToolSpec>emptyList());

        assertFalse(response.hasToolCalls());
        assertEquals("Сегодня среда.", response.getContent());
    }

    @Test
    public void parsesToolCallsWhenModelRequestsToolExecution() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                        + "\"tool_calls\":[{\"id\":\"call-9\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"find_items\",\"arguments\":\"{\\\"query\\\":\\\"\\\"}\"}}]}}]}"));

        HttpLlmClient client = new HttpLlmClient(
                server.url("/").toString(), "test-key", "gpt-test", 5000);

        ChatResponse response = client.chat(
                Collections.singletonList(ChatMessage.user("покажи напоминания")),
                Collections.<ToolSpec>emptyList());

        assertTrue(response.hasToolCalls());
        assertEquals(1, response.getToolCalls().size());
        ToolCall toolCall = response.getToolCalls().get(0);
        assertEquals("call-9", toolCall.getId());
        assertEquals("find_items", toolCall.getName());
        assertEquals("{\"query\":\"\"}", toolCall.getArgumentsJson());
    }

    @Test(expected = LlmClientException.class)
    public void throwsLlmClientExceptionOnNonSuccessfulHttpStatus() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"boom\"}"));

        HttpLlmClient client = new HttpLlmClient(
                server.url("/").toString(), "test-key", "gpt-test", 5000);

        client.chat(Collections.singletonList(ChatMessage.user("x")), Collections.<ToolSpec>emptyList());
    }

    @Test(expected = LlmClientException.class)
    public void throwsLlmClientExceptionWhenServerIsSlowerThanConfiguredTimeout() {
        server.enqueue(new MockResponse().setBody("{}").setHeadersDelay(2, TimeUnit.SECONDS));

        HttpLlmClient client = new HttpLlmClient(
                server.url("/").toString(), "test-key", "gpt-test", 200);

        client.chat(Collections.singletonList(ChatMessage.user("x")), Collections.<ToolSpec>emptyList());
    }
}
