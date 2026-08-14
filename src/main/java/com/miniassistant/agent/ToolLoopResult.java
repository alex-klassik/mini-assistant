package com.miniassistant.agent;

import java.util.Collections;
import java.util.List;

/**
 * Итог одного прогона {@link ToolLoop}: либо модель дала финальный текстовый
 * ответ ({@link #isCompleted()} == true, {@link #getFinalAnswer()} заполнен),
 * либо лимит шагов исчерпан прежде, чем модель дала финал ({@link #isCompleted()}
 * == false, {@link #getFinalAnswer()} {@code null}). Формулировку
 * фолбэк-ответа пользователю в этом случае решает {@code AgentService} (M6/M8),
 * не {@code ToolLoop}.
 *
 * <p>{@link #getCalledToolNames()} - имена всех инструментов, которые модель
 * запросила за время прогона (в порядке вызова), независимо от того,
 * выполнились ли они успешно - сам факт попытки вызова уже важен для аудита
 * (M13), который ведёт {@code AgentService}.
 */
public final class ToolLoopResult {

    private final boolean completed;
    private final String finalAnswer;
    private final List<String> calledToolNames;

    private ToolLoopResult(boolean completed, String finalAnswer, List<String> calledToolNames) {
        this.completed = completed;
        this.finalAnswer = finalAnswer;
        this.calledToolNames = calledToolNames;
    }

    public static ToolLoopResult finalAnswer(String content, List<String> calledToolNames) {
        return new ToolLoopResult(true, content, calledToolNames);
    }

    public static ToolLoopResult stepLimitReached(List<String> calledToolNames) {
        return new ToolLoopResult(false, null, calledToolNames);
    }

    public boolean isCompleted() {
        return completed;
    }

    /** {@code null}, если {@link #isCompleted()} == false. */
    public String getFinalAnswer() {
        return finalAnswer;
    }

    public List<String> getCalledToolNames() {
        return Collections.unmodifiableList(calledToolNames);
    }
}
