package com.miniassistant.app;

import com.miniassistant.agent.AgentService;
import com.miniassistant.agent.ToolLoop;
import com.miniassistant.audit.AuditLog;
import com.miniassistant.audit.HmacSigner;
import com.miniassistant.config.AppConfig;
import com.miniassistant.config.ConfigLoader;
import com.miniassistant.config.EnvProvider;
import com.miniassistant.config.SystemEnvProvider;
import com.miniassistant.llm.HttpLlmClient;
import com.miniassistant.llm.LlmClient;
import com.miniassistant.logging.PiiMasker;
import com.miniassistant.mail.MailChannel;
import com.miniassistant.mail.OutlookMailChannel;
import com.miniassistant.store.SeenStore;
import com.miniassistant.tools.AddReminderTool;
import com.miniassistant.tools.CurrentDatetimeTool;
import com.miniassistant.tools.FindItemsTool;
import com.miniassistant.tools.ReminderStore;
import com.miniassistant.tools.Tool;
import com.miniassistant.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Точка входа: собирает все компоненты (M0-M12) по конфигу из аргумента
 * командной строки и запускает бесконечный цикл опроса почты.
 *
 * <p>Класс разделён на тестируемую и нетестируемую части (M13):
 * {@link #buildAgentService} - чистая сборка объектов из {@link AppConfig},
 * без обращения к Outlook и без {@code System.exit} - это можно и нужно
 * юнит-тестировать (см. {@code MainTest}: {@code MockMailChannel} вместо
 * Outlook, {@code MockWebServer} вместо реального LLM-провайдера). А вот
 * {@link #main} и {@link #pollLoop} - реальный COM-объект
 * {@link OutlookMailChannel} (тот же случай, что и M11: живая зависимость
 * ОС, не воспроизводимая в CI), бесконечный цикл с реальным
 * {@link Thread#sleep} и {@code System.exit} при фатальной ошибке - честно
 * не юнит-тестируются, а проверяются вручную по
 * {@code docs/M13-main-manual-checklist.md}.
 */
public final class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar mini-assistant.jar <config.yaml>");
            System.exit(1);
            return;
        }

        try {
            AppConfig config = new ConfigLoader().load(Paths.get(args[0]));
            EnvProvider env = new SystemEnvProvider();

            try (OutlookMailChannel mailChannel = new OutlookMailChannel(
                    config.getMail().getProfile(), config.getMail().getFolder())) {
                AgentService agentService = buildAgentService(config, env, mailChannel);

                AtomicBoolean running = new AtomicBoolean(true);
                Thread mainThread = Thread.currentThread();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    running.set(false);
                    mainThread.interrupt();
                }));

                logger.info("event=agent_started pollSeconds={}", config.getMail().getPollSeconds());
                pollLoop(agentService, config.getMail().getPollSeconds(), running);
                logger.info("event=agent_stopped");
            }
        } catch (RuntimeException e) {
            logger.error("event=agent_fatal_error error={}", PiiMasker.mask(e.toString()));
            System.exit(1);
        }
    }

    /** Чистая сборка {@link AgentService} из конфига - без Outlook, без System.exit. Юнит-тестируется в {@code MainTest}. */
    static AgentService buildAgentService(AppConfig config, EnvProvider env, MailChannel mailChannel) {
        LlmClient llmClient = new HttpLlmClient(
                config.getLlm().getEndpoint(),
                config.getLlm().resolveApiKey(env),
                config.getLlm().getModel(),
                config.getLlm().getTimeoutMs());

        Path storePath = Paths.get(config.getStore().getPath());
        SeenStore seenStore = new SeenStore(storePath.resolve("seen.txt"));
        ReminderStore reminderStore = new ReminderStore(storePath.resolve("reminders.json"));
        AuditLog auditLog = new AuditLog(
                storePath.resolve("audit.jsonl"),
                new HmacSigner(config.getAudit().resolveHmacKey(env)));

        List<Tool> tools = Arrays.asList(
                new CurrentDatetimeTool(Clock.systemUTC()),
                new AddReminderTool(reminderStore),
                new FindItemsTool(reminderStore));
        ToolRegistry registry = new ToolRegistry(tools);

        ToolLoop toolLoop = new ToolLoop(llmClient, registry, config.getAgent().getMaxSteps());

        return new AgentService(mailChannel, toolLoop, seenStore, auditLog);
    }

    /** Бесконечный опрос с паузой {@code pollSeconds}; прерывается через {@code running} (shutdown hook в {@link #main}). */
    static void pollLoop(AgentService agentService, int pollSeconds, AtomicBoolean running) {
        while (running.get()) {
            agentService.processUnread();
            try {
                Thread.sleep(pollSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
