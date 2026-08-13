package com.miniassistant.mail;

import java.util.List;

/**
 * Контракт почтового канала, за которым прячется конкретная реализация
 * (Outlook через JACOB в бою, {@link MockMailChannel} в тестах).
 */
public interface MailChannel {

    /**
     * Непрочитанные письма на текущий момент. Реализация сама решает, что
     * значит "непрочитанное" (Outlook: свойство UnRead; мок: заранее заданный
     * список).
     */
    List<Msg> fetchUnread();

    /**
     * Отправить ответ отправителю исходного письма.
     *
     * @param original письмо, на которое отвечаем
     * @param body     текст ответа
     */
    void reply(Msg original, String body);
}
