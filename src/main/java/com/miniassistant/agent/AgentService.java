package com.miniassistant.agent;

import com.miniassistant.llm.ChatMessage;
import com.miniassistant.mail.MailChannel;
import com.miniassistant.mail.Msg;
import com.miniassistant.store.SeenStore;

import java.util.Arrays;
import java.util.List;

/**
 * Склеивает почтовый канал, tool-loop и хранилище идемпотентности в одну
 * операцию опроса: непрочитанные письма -> по каждому прогон через
 * {@link ToolLoop} -> ответ письмом. Письма, чей id уже есть в
 * {@link SeenStore}, повторно не обрабатываются - это и есть идемпотентность
 * при повторном {@link MailChannel#fetchUnread()} (например, после рестарта
 * процесса на том же файле SeenStore).
 */
public final class AgentService {

    private static final String SYSTEM_PROMPT =
            "Ты - почтовый ассистент. Отвечай кратко и по делу, используя "
                    + "доступные инструменты, если запрос того требует.";

    private static final String STEP_LIMIT_FALLBACK =
            "Извините, не удалось обработать запрос за отведённое число шагов.";

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
            mailChannel.reply(msg, answerFor(msg));
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
