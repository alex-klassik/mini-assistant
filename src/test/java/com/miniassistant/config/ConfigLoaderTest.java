package com.miniassistant.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

public class ConfigLoaderTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final String SAMPLE_YAML =
            "llm:\n"
                    + "  endpoint: \"https://api.openai.com/v1/chat/completions\"\n"
                    + "  model: \"gpt-4o-mini\"\n"
                    + "  apiKeyEnv: \"LLM_API_KEY\"\n"
                    + "  timeoutMs: 15000\n"
                    + "agent:\n"
                    + "  maxSteps: 5\n"
                    + "store:\n"
                    + "  path: \"./data\"\n"
                    + "mail:\n"
                    + "  pollSeconds: 30\n"
                    + "  profile: \"Outlook\"\n"
                    + "  folder: \"Inbox\"\n"
                    + "audit:\n"
                    + "  hmacKeyEnv: \"AUDIT_HMAC_KEY\"\n";

    @Test
    public void loadsAllFieldsFromYamlSampleFromPlan() throws IOException {
        File file = tempFolder.newFile("config.yaml");
        Files.write(file.toPath(), SAMPLE_YAML.getBytes(StandardCharsets.UTF_8));

        ConfigLoader loader = new ConfigLoader();
        AppConfig config = loader.load(file.toPath());

        assertEquals("https://api.openai.com/v1/chat/completions", config.getLlm().getEndpoint());
        assertEquals("gpt-4o-mini", config.getLlm().getModel());
        assertEquals("LLM_API_KEY", config.getLlm().getApiKeyEnv());
        assertEquals(15000, config.getLlm().getTimeoutMs());

        assertEquals(5, config.getAgent().getMaxSteps());

        assertEquals("./data", config.getStore().getPath());

        assertEquals(30, config.getMail().getPollSeconds());
        assertEquals("Outlook", config.getMail().getProfile());
        assertEquals("Inbox", config.getMail().getFolder());

        assertEquals("AUDIT_HMAC_KEY", config.getAudit().getHmacKeyEnv());
    }

    @Test
    public void resolvesApiKeyFromInjectableEnvProviderWithoutTouchingRealEnv() {
        LlmConfig llm = new LlmConfig();
        llm.setApiKeyEnv("LLM_API_KEY");
        EnvProvider fakeEnv = new EnvProvider() {
            @Override
            public String getenv(String name) {
                return "LLM_API_KEY".equals(name) ? "sk-test-secret" : null;
            }
        };

        assertEquals("sk-test-secret", llm.resolveApiKey(fakeEnv));
    }

    @Test(expected = IllegalStateException.class)
    public void resolveApiKeyThrowsWhenDeclaredEnvVarIsNotSet() {
        LlmConfig llm = new LlmConfig();
        llm.setApiKeyEnv("MISSING_VAR");
        EnvProvider emptyEnv = new EnvProvider() {
            @Override
            public String getenv(String name) {
                return null;
            }
        };

        llm.resolveApiKey(emptyEnv);
    }

    @Test
    public void resolvesHmacKeyFromInjectableEnvProviderWithoutTouchingRealEnv() {
        AuditConfig audit = new AuditConfig();
        audit.setHmacKeyEnv("AUDIT_HMAC_KEY");
        EnvProvider fakeEnv = new EnvProvider() {
            @Override
            public String getenv(String name) {
                return "AUDIT_HMAC_KEY".equals(name) ? "hmac-test-secret" : null;
            }
        };

        assertEquals("hmac-test-secret", audit.resolveHmacKey(fakeEnv));
    }

    @Test(expected = IllegalStateException.class)
    public void resolveHmacKeyThrowsWhenDeclaredEnvVarIsNotSet() {
        AuditConfig audit = new AuditConfig();
        audit.setHmacKeyEnv("MISSING_VAR");
        EnvProvider emptyEnv = new EnvProvider() {
            @Override
            public String getenv(String name) {
                return null;
            }
        };

        audit.resolveHmacKey(emptyEnv);
    }
}
