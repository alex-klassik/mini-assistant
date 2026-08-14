package com.miniassistant.agent;

import com.miniassistant.audit.AuditLog;
import com.miniassistant.llm.ChatMessage;
import com.miniassistant.logging.Events;
import com.miniassistant.logging.PiiMasker;
import com.miniassistant.mail.MailChannel;
import com.miniassistant.mail.Msg;
import com.miniassistant.store.SeenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
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
 * один сбой отправки не должны обрывать обработку всего батча писем. Текст
 * тела письма в лог никогда не попадает; текст исключения перед записью
 * дополнительно прогоняется через {@link PiiMasker} (M10) - на случай, если
 * сторонняя ошибка (например, от почтового сервера) сама содержит email.
 *
 * <p>В {@link AuditLog} (M9/M13) пишем только на полностью успешном пути -
 * после того, как ответ реально отправлен и письмо помечено seen: по одной
 * записи {@code tool_called} на каждый вызванный инструмент, затем одна
 * запись {@code mail_processed}. Если LLM упал или письмо не удалось
 * отправить, в аудит ничего не попадает - это уже видно в обычных
 * WARN-логах выше; аудит фиксирует только реально свершившиеся действия.
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
    private final AuditLog auditLog;

    public AgentService(MailChannel mailChannel, ToolLoop toolLoop, SeenStore seenStore, AuditLog auditLog) {
        this.mailChannel = mailChannel;
        this.toolLoop = toolLoop;
        this.seenStore = seenStore;
        this.auditLog = auditLog;
    }

    public void processUnread() {
        for (Msg msg : mailChannel.fetchUnread()) {
            if (seenStore.isSeen(msg.getId())) {
                continue;
            }

            String answer;
            List<String> calledToolNames = Collections.emptyList();
            try {
                ToolLoopResult result = runToolLoop(msg);
                answer = result.isCompleted() ? result.getFinalAnswer() : STEP_LIMIT_FALLBACK;
                calledToolNames = result.getCalledToolNames();
            } catch (RuntimeException e) {
                logger.warn("event={} msgId={} error={}", Events.LLM_FAILED, msg.getId(),
                        PiiMasker.mask(e.toString()));
                answer = LLM_FAILURE_FALLBACK;
            }

            try {
                mailChannel.reply(msg, answer);
            } catch (RuntimeException e) {
                logger.warn("event={} msgId={} error={}", Events.MAIL_SEND_FAILED, msg.getId(),
                        PiiMasker.mask(e.toString()));
                continue;
            }

            seenStore.markSeen(msg.getId());
            for (String toolName : calledToolNames) {
                auditLog.append("event=" + Events.TOOL_CALLED + " tool=" + toolName);
            }
            auditLog.append("event=" + Events.MAIL_PROCESSED + " msgId=" + msg.getId());
        }
    }

    private ToolLoopResult runToolLoop(Msg msg) {
        List<ChatMessage> initialMessages = Arrays.asList(
                ChatMessage.system(SYSTEM_PROMPT),
                ChatMessage.user(msg.getBody()));
        return toolLoop.run(initialMessages);
    }
}
