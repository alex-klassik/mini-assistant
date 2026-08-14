package com.miniassistant.mail;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.ComThread;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Реализация {@link MailChannel} поверх Outlook через COM-мост JACOB.
 *
 * <p>Намеренно без юнит-теста (M11 по PLAN.md): Outlook - это живой COM-объект
 * операционной системы, а не что-то, что можно поднять или подменить в CI без
 * установленного Outlook и нативной {@code jacob-*.dll} на PATH (см. комментарий
 * в pom.xml про исключение jacob из test-classpath). Вместо юнит-теста -
 * чек-лист ручной проверки на живом Outlook: {@code docs/M11-outlook-manual-checklist.md}.
 *
 * <p>Идемпотентность обработки писем на этом уровне не решается: {@link #fetchUnread()}
 * просто возвращает то, что Outlook сам считает непрочитанным (свойство
 * {@code UnRead}) на момент вызова, ничего не помечая прочитанным. Эта
 * реализация не трогает данный флаг сознательно - "не обрабатывать письмо
 * повторно" уже гарантирует {@link com.miniassistant.store.SeenStore} на
 * уровне {@link com.miniassistant.agent.AgentService} (M2/M6), и держать два
 * независимых источника истины о том, что уже обработано, было бы избыточно
 * и могло бы рассинхронизироваться (например, если письмо когда-то откроют в
 * самом Outlook вручную).
 */
public final class OutlookMailChannel implements MailChannel, AutoCloseable {

    private static final int OL_FOLDER_INBOX = 6;

    private final ActiveXComponent outlook;
    private final Dispatch namespace;
    private final Dispatch folderItems;

    /**
     * @param profile имя Outlook-профиля для входа ({@code Namespace.Logon}); если
     *                {@code null} или пусто - подключаемся к уже запущенной сессии
     *                Outlook без повторного логона (обычный случай, когда Outlook
     *                уже открыт и залогинен пользователем)
     * @param folder  имя папки для опроса; {@code null}, пусто или {@code "Inbox"} -
     *                стандартная папка "Входящие", иначе - подпапка "Входящих" с
     *                этим именем
     */
    public OutlookMailChannel(String profile, String folder) {
        ComThread.InitSTA();
        this.outlook = new ActiveXComponent("Outlook.Application");
        this.namespace = Dispatch.call(outlook, "GetNamespace", "MAPI").toDispatch();
        if (profile != null && !profile.trim().isEmpty()) {
            Dispatch.call(namespace, "Logon", profile, "", false, false);
        }
        Dispatch inbox = Dispatch.call(namespace, "GetDefaultFolder", new Variant(OL_FOLDER_INBOX)).toDispatch();
        Dispatch resolvedFolder = resolveFolder(inbox, folder);
        this.folderItems = Dispatch.get(resolvedFolder, "Items").toDispatch();
    }

    @Override
    public List<Msg> fetchUnread() {
        Dispatch unread = Dispatch.call(folderItems, "Restrict", "[UnRead] = true").toDispatch();
        int count = Dispatch.get(unread, "Count").getInt();

        List<Msg> messages = new ArrayList<Msg>(count);
        for (int i = 1; i <= count; i++) {
            Dispatch item = Dispatch.call(unread, "Item", new Variant(i)).toDispatch();
            messages.add(toMsg(item));
        }
        return messages;
    }

    @Override
    public void reply(Msg original, String body) {
        Dispatch originalItem = Dispatch.call(namespace, "GetItemFromID", original.getId()).toDispatch();
        Dispatch replyItem = Dispatch.call(originalItem, "Reply").toDispatch();
        Dispatch.put(replyItem, "Body", body);
        Dispatch.call(replyItem, "Send");
    }

    @Override
    public void close() {
        outlook.safeRelease();
        ComThread.Release();
    }

    private static Dispatch resolveFolder(Dispatch inbox, String folderName) {
        if (folderName == null || folderName.trim().isEmpty() || "Inbox".equalsIgnoreCase(folderName)) {
            return inbox;
        }
        Dispatch subFolders = Dispatch.call(inbox, "Folders").toDispatch();
        return Dispatch.call(subFolders, "Item", folderName).toDispatch();
    }

    private static Msg toMsg(Dispatch item) {
        String id = Dispatch.get(item, "EntryID").getString();
        String from = Dispatch.get(item, "SenderEmailAddress").getString();
        String subject = Dispatch.get(item, "Subject").getString();
        String body = Dispatch.get(item, "Body").getString();
        Instant receivedAt = Dispatch.get(item, "ReceivedTime").getJavaDate().toInstant();
        return new Msg(id, from, subject, body, receivedAt);
    }
}
