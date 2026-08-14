package com.miniassistant.agent;

import com.miniassistant.llm.ChatMessage;
import com.miniassistant.mail.MailChannel;
import com.miniassistant.mail.Msg;
import com.miniassistant.store.SeenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Склеивает почтовый канал, tool-loop и хранилище идемпотентности в одну
 * операцию опроса: непрочитанные письма -> по каждому прогон через
 * {@link ToolLoop} -> ответ письмом. Письма, чей id уже есть в
 * {@link SeenStore}, повторно не обрабатываются - это и есть идемпотентность
 * при повторном {@link MailChannel#fetchUnread()} (например, после рестарта
 * процесса на том же файле SeenStore).
 *
 * <p>Два независимых источника сбоя обрабатываются по-разному (M8):
 * <ul>
 *   <li>{@link ToolLoop#run} (то есть, в конечном счёте, {@code LlmClient})
 *       бросает исключение - письмо считается обработанным: пользователю
 *       уходит фолбэк-ответ {@link #LLM_FAILURE_FALLBACK}, письмо помечается
 *       seen, чтобы не отвечать ему повторно на следующем опросе;</li>
 *   <li>{@link MailChannel#reply} бросает исключение (например, эмуляция
 *       COM-ошибки Outlook) - ответ пользователю физически не доставлен,
 *       поэтому письмо seen НЕ помечается и будет обработано заново на
 *       следующем опросе; обработка остальных писем батча продолжается.</li>
 * </ul>
 * В обоих случаях исключение гасится и пишется в лог как WARN, наружу
 * {@code processUnread()} не бросает ничего - один плохой ответ модели или
 * один сбой отправки не должны обрывать обработку всего батча писем.
 */
public final class AgentService {

    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);

    private static final String SYSTEM_PROMPT =
            "Ты - почтовый ассистент. Отвечай кратко и по делу, используя "
                    + "доступные инструменты, если запрос того требует.";

    private static final String STEP_LIMIT_FALLBACK =
            "Извините, не удалось обработать запрос за отведённое число шагов.";

    public static final String LLM_FAILURE_FALLBACK =
            "Извините, при обработке вашего запроса произошла техническая ошибка. "
                    + "Пожалуйста, попробуйте отправить письмо ещё раз позже.";

    private final MailChannel mailChannel;
    private final ToolLoop toolLoop;
    private final SeenStore seenStore;

    public AgentService(MailChannel mailChannel, ToolLoop toolLoop, SeenStore seenStore) {
        this.mailChannel = mailChannel;
        this.toolLoop = toolLoop;
        this.seenStore = seenStore;
    }

    public void processUnread() {
        for (Msg msg : mailChannel.fetchUnread()) {
            if (seenStore.isSeen(msg.getId())) {
                continue;
            }

            String answer;
            try {
                answer = answerFor(msg);
            } catch (RuntimeException e) {
                logger.warn("event=llm_failed msgId={} error={}", msg.getId(), e.toString());
                answer = LLM_FAILURE_FALLBACK;
            }

            try {
                mailChannel.reply(msg, answer);
            } catch (RuntimeException e) {
                logger.warn("event=mail_send_failed msgId={} error={}", msg.getId(), e.toString());
                continue;
            }

            seenStore.markSeen(msg.getId());
        }
    }

    private String answerFor(Msg msg) {
        List<ChatMessage> initialMessages = Arrays.asList(
                ChatMessage.system(SYSTEM_PROMPT),
                ChatMessage.user(msg.getBody()));
        ToolLoopResult result = toolLoop.run(initialMessages);
        return result.isCompleted() ? result.getFinalAnswer() : STEP_LIMIT_FALLBACK;
    }
}
