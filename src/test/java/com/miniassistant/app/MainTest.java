package com.miniassistant.app;

import com.miniassistant.agent.AgentService;
import com.miniassistant.config.AgentConfig;
import com.miniassistant.config.AppConfig;
import com.miniassistant.config.AuditConfig;
import com.miniassistant.config.EnvProvider;
import com.miniassistant.config.LlmConfig;
import com.miniassistant.config.MailConfig;
import com.miniassistant.config.StoreConfig;
import com.miniassistant.mail.MockMailChannel;
import com.miniassistant.mail.Msg;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Проверяет только {@link Main#buildAgentService} - чистую сборку объектов из
 * {@link AppConfig} без обращения к реальному Outlook (вместо него -
 * {@link MockMailChannel}) и без реального LLM-провайдера (вместо него -
 * {@link MockWebServer}, как в {@code HttpLlmClientTest}). {@code main}/
 * {@code pollLoop} здесь не проверяются - см. Javadoc {@link Main}.
 */
public class MainTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

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
    public void buildAgentServiceWiresConfigIntoAWorkingAgentService() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                        + "\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"add_reminder\",\"arguments\":"
                        + "\"{\\\"text\\\":\\\"позвонить\\\","
                        + "\\\"dueIso\\\":\\\"2026-08-15T15:00:00Z\\\"}\"}}]}}]}"));
        server.enqueue(new MockResponse().setBody(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Напоминание добавлено.\"}}]}"));

        AppConfig config = testConfig();
        EnvProvider env = testEnv();
        Msg msg = new Msg("msg-1", "user@example.com", "Напоминание",
                "Напомни позвонить",
                Instant.parse("2026-08-14T09:00:00Z"));
        MockMailChannel mailChannel = new MockMailChannel(msg);

        AgentService agentService = Main.buildAgentService(config, env, mailChannel);
        agentService.processUnread();

        assertEquals(1, mailChannel.repliesSent().size());
        assertEquals("Напоминание добавлено.",
                mailChannel.repliesSent().get(0).body);
        assertTrue("ReminderStore должен был создать файл в store.path",
                Files.exists(new File(tempFolder.getRoot(), "reminders.json").toPath()));
        assertTrue("AuditLog должен был создать файл в store.path",
                Files.exists(new File(tempFolder.getRoot(), "audit.jsonl").toPath()));
    }

    private AppConfig testConfig() {
        AppConfig config = new AppConfig();

        LlmConfig llm = new LlmConfig();
        llm.setEndpoint(server.url("/v1/chat/completions").toString());
        llm.setModel("gpt-test");
        llm.setApiKeyEnv("TEST_LLM_KEY");
        llm.setTimeoutMs(5000);
        config.setLlm(llm);

        AgentConfig agent = new AgentConfig();
        agent.setMaxSteps(5);
        config.setAgent(agent);

        StoreConfig store = new StoreConfig();
        store.setPath(tempFolder.getRoot().getAbsolutePath());
        config.setStore(store);

        MailConfig mail = new MailConfig();
        mail.setPollSeconds(30);
        mail.setProfile("Outlook");
        mail.setFolder("Inbox");
        config.setMail(mail);

        AuditConfig audit = new AuditConfig();
        audit.setHmacKeyEnv("TEST_HMAC_KEY");
        config.setAudit(audit);

        return config;
    }

    private EnvProvider testEnv() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("TEST_LLM_KEY", "test-llm-key");
        values.put("TEST_HMAC_KEY", "test-hmac-key");
        return new EnvProvider() {
            @Override
            public String getenv(String name) {
                return values.get(name);
            }
        };
    }
}
