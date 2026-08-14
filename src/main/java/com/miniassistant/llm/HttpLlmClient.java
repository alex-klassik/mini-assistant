package com.miniassistant.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * {@link LlmClient} поверх HTTP - отправляет Chat Completions запросы (формат,
 * совместимый с OpenAI: JSON-тело с полями {@code model}/{@code messages}/
 * {@code tools}, ответ в {@code choices[0].message}) через okhttp. Транспортные
 * детали (таймауты, заголовок авторизации, разбор JSON) - забота этого класса;
 * {@code ToolLoop} (M5) о них не знает и работает только через интерфейс
 * {@link LlmClient}.
 *
 * <p>Конкретный вендор/endpoint не хардкодится - оба приходят в конструктор
 * снаружи (из конфига, M7), как и API-ключ, который до этого класса уже
 * резолвится из переменной окружения ({@code LlmConfig#resolveApiKey}) - сюда
 * попадает только готовое значение, секрет здесь не читается напрямую из env.
 */
public final class HttpLlmClient implements LlmClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpLlmClient(String endpoint, String apiKey, String model, int timeoutMs) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<ToolSpec> tools) {
        Request request = new Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(JSON, buildRequestBody(messages, tools)))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new LlmClientException("LLM endpoint returned HTTP " + response.code());
            }
            ResponseBody body = response.body();
            return parseResponseBody(body != null ? body.string() : "");
        } catch (IOException e) {
            throw new LlmClientException("failed to call LLM endpoint", e);
        }
    }

    private String buildRequestBody(List<ChatMessage> messages, List<ToolSpec> tools) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);

        ArrayNode messagesNode = root.putArray("messages");
        for (ChatMessage message : messages) {
            messagesNode.add(toMessageNode(message));
        }

        if (!tools.isEmpty()) {
            ArrayNode toolsNode = root.putArray("tools");
            for (ToolSpec tool : tools) {
                toolsNode.add(toToolNode(tool));
            }
        }
        return root.toString();
    }

    private ObjectNode toMessageNode(ChatMessage message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", message.getRole().name().toLowerCase(Locale.ROOT));
        if (message.getContent() != null) {
            node.put("content", message.getContent());
        }
        if (message.getToolCallId() != null) {
            node.put("tool_call_id", message.getToolCallId());
        }
        if (!message.getToolCalls().isEmpty()) {
            ArrayNode toolCallsNode = node.putArray("tool_calls");
            for (ToolCall toolCall : message.getToolCalls()) {
                toolCallsNode.add(toToolCallNode(toolCall));
            }
        }
        return node;
    }

    private ObjectNode toToolCallNode(ToolCall toolCall) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", toolCall.getId());
        node.put("type", "function");
        ObjectNode function = node.putObject("function");
        function.put("name", toolCall.getName());
        function.put("arguments", toolCall.getArgumentsJson());
        return node;
    }

    private ObjectNode toToolNode(ToolSpec tool) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "function");
        ObjectNode function = node.putObject("function");
        function.put("name", tool.getName());
        function.put("description", tool.getDescription());
        function.set("parameters", parseJsonSchema(tool));
        return node;
    }

    private JsonNode parseJsonSchema(ToolSpec tool) {
        try {
            return mapper.readTree(tool.getParametersJsonSchema());
        } catch (IOException e) {
            throw new LlmClientException(
                    "tool '" + tool.getName() + "' has invalid parameters JSON schema", e);
        }
    }

    private ChatResponse parseResponseBody(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode message = root.path("choices").path(0).path("message");
            JsonNode toolCallsNode = message.path("tool_calls");
            if (toolCallsNode.isArray() && toolCallsNode.size() > 0) {
                return ChatResponse.toolCalls(toToolCalls(toolCallsNode));
            }
            return ChatResponse.text(textOrNull(message.path("content")));
        } catch (IOException e) {
            throw new LlmClientException("failed to parse LLM response", e);
        }
    }

    private List<ToolCall> toToolCalls(JsonNode toolCallsNode) {
        List<ToolCall> toolCalls = new ArrayList<ToolCall>();
        for (JsonNode node : toolCallsNode) {
            JsonNode function = node.path("function");
            toolCalls.add(new ToolCall(
                    node.path("id").asText(),
                    function.path("name").asText(),
                    function.path("arguments").asText()));
        }
        return toolCalls;
    }

    private static String textOrNull(JsonNode node) {
        return (node.isMissingNode() || node.isNull()) ? null : node.asText();
    }
}
