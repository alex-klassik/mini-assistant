package com.miniassistant.agent;

/**
 * Итог одного прогона {@link ToolLoop}: либо модель дала финальный текстовый
 * ответ ({@link #isCompleted()} == true, {@link #getFinalAnswer()} заполнен),
 * либо лимит шагов исчерпан прежде, чем модель дала финал ({@link #isCompleted()}
 * == false, {@link #getFinalAnswer()} {@code null}). Формулировку
 * фолбэк-ответа пользователю в этом случае решает {@code AgentService} (M6/M8),
 * не {@code ToolLoop}.
 */
public final class ToolLoopResult {

    private final boolean completed;
    private final String finalAnswer;

    private ToolLoopResult(boolean completed, String finalAnswer) {
        this.completed = completed;
        this.finalAnswer = finalAnswer;
    }

    public static ToolLoopResult finalAnswer(String content) {
        return new ToolLoopResult(true, content);
    }

    public static ToolLoopResult stepLimitReached() {
        return new ToolLoopResult(false, null);
    }

    public boolean isCompleted() {
        return completed;
    }

    /** {@code null}, если {@link #isCompleted()} == false. */
    public String getFinalAnswer() {
        return finalAnswer;
    }
}
