# PLAN.md — mini-assistant

Mini-ассистент по образу «Коли»: читает непрочитанные письма из Outlook (JACOB),
прогоняет тело письма через LLM с tool-calling, отвечает письмом. План написан
**до** прод-кода (Plan-first). Разработка идёт по TDD: для каждой вехи сначала
падающий тест, потом реализация, каждый переход red→green — отдельный коммит.

## 1. Цель и критерии приёмки

Цикл ядра: `опросить входящие → на каждое непрочитанное письмо прогнать LLM
tool-loop с телом письма как запросом → ответить письмом отправителю`. Письмо —
единица диалога.

Оценивается независимо:
1. Инженерное ядро (см. §2–§5 ниже) — работает ли агент по стеку.
2. Подход к работе (Plan-first, TDD, graceful-фолбэки, секреты из env,
   структурные логи, security-review, атомарные коммиты, verification-before-completion).

Обязательное условие: `mvn test` зелёный **без Outlook** (на CI/Linux) — JACOB
исключается из test-classpath.

## 2. Архитектура

Пакеты и границы ответственности:

- **`config`** — `AppConfig` (POJO), `ConfigLoader` (Jackson + YAML).
- **`mail`** — `Msg` (id/from/subject/body/receivedAt), `MailChannel` (интерфейс:
  `List<Msg> fetchUnread()`, `void reply(Msg, String body)`), `OutlookMailChannel`
  (JACOB), `MockMailChannel` (для тестов).
- **`llm`** — `LlmClient` (интерфейс: `ChatResponse chat(List<ChatMessage>, List<ToolSpec>)`),
  DTO `ChatMessage`/`ToolSpec`/`ToolCall`/`ChatResponse`, `HttpLlmClient` (okhttp,
  OpenAI-совместимый Chat Completions с `tools`/`tool_calls`), `MockLlmClient`
  (скриптуемые ответы для тестов).
- **`tools`** — `Tool` (интерфейс: `name()`, `description()`, `jsonSchema()`,
  `String execute(String argsJson)`), `CurrentDatetimeTool` (инжектируемый
  `java.time.Clock` — детерминизм в тестах), `AddReminderTool`, `FindItemsTool`,
  `ReminderStore` (JSON-файл на диске), `ToolRegistry`.
- **`agent`** — `ToolLoop` (цикл с лимитом `maxSteps`, устойчив к
  галлюцинированному/неизвестному `tool_call` — не падает, возвращает модели
  структурированную ошибку), `AgentService` (склейка: письмо → tool-loop →
  ответ, оборачивает graceful-фолбэки).
- **`store`** — `SeenStore` (идемпотентность: файл на диске, ключ — Outlook
  EntryID / Message-ID, переживает рестарт процесса).
- **`audit`** — `HmacSigner` (HMAC-SHA256 цепочка хешей, ключ из env),
  `AuditLog` (append-only JSONL: какое письмо обработано, какие tool_call).
- **`logging`** — `Events` (константы event-key: `agent_mail_seen`,
  `agent_tool_call`, `llm_failed`, ...), `PiiMasker` (маскирование email/тела
  перед любым логом).
- **`app`** — `Main` (сборка зависимостей по конфигу, poll-loop с
  `mail.pollSeconds`, graceful shutdown).

Зависимости между пакетами идут в одну сторону: `app` → `agent` → (`mail`,
`llm`, `tools`, `store`, `audit`, `logging`) → `config`. Тесты подставляют
`Mock*`/`Fake*` реализации везде, где граница — интерфейс.

## 3. Стек и версии (совместимость с Java 8 проверена заранее)

| Артефакт | Версия | Примечание |
|---|---|---|
| `net.sf.jacob-project:jacob` | `1.20`, classifier `x64` | exclude из test-classpath (surefire `classpathDependencyExcludes`) — иначе статический инициализатор роняет JVM на Linux/CI |
| `com.squareup.okhttp3:okhttp` | `3.14.9` | последняя версия до перехода на Kotlin, чистая Java |
| `com.fasterxml.jackson.core:jackson-databind` + `jackson-dataformat-yaml` | `2.15.x` | конфиг + сериализация JSON-сторов |
| `org.slf4j:slf4j-api` | `1.7.36` | |
| `ch.qos.logback:logback-classic` | `1.2.12` | logback 1.3+/1.4+ требует Java 11 — не берём |
| `junit:junit` | `4.13.2` | по заданию именно JUnit 4 |
| `okhttp3:mockwebserver` | `3.14.9` | test-scope, для юнит-теста `HttpLlmClient` без реальной сети |

Без Mockito: `MockMailChannel`/`MockLlmClient` — рукописные фейки (это прямо
требует задание и снижает риск конфликта версий).

Maven-плагины: `maven-compiler-plugin` (`release=8`), `maven-shade-plugin`
(fat-jar, `Main-Class` в манифесте), `maven-surefire-plugin` с
`classpathDependencyExcludes` для `net.sf.jacob-project:jacob`.

Координаты: `groupId=com.miniassistant`, `artifactId=mini-assistant`,
`version=0.1.0-SNAPSHOT`.

## 4. Контракты инструментов (tool-calling)

Формат — OpenAI-style function calling: каждый `Tool` отдаёт `name`,
`description`, JSON Schema параметров; модель возвращает `tool_calls` с
`arguments` в виде JSON-строки; `ToolLoop` вызывает `execute(argsJson)` и
кладёт результат обратно как `role=tool` сообщение.

- `current_datetime()` → `{"iso": "<текущее время через инжектируемый Clock>"}`.
- `add_reminder(text: string, dueIso: string)` → запись в `ReminderStore`
  (JSON-файл), возврат `{"id": "...", "text": "...", "dueIso": "..."}`.
- `find_items(query: string)` → поиск по `ReminderStore` (простое
  подстроковое совпадение по `text`), возврат списка совпадений.

Ошибки инструмента (невалидный JSON аргументов, неизвестное имя tool,
исключение внутри `execute`) **никогда** не прокидываются наружу как
исключение из `ToolLoop` — они превращаются в `{"error": "..."}`,
возвращаются модели как результат tool-вызова, цикл продолжается.

## 5. Конфигурация (YAML)

```yaml
llm:
  endpoint: "https://api.openai.com/v1/chat/completions"
  model: "gpt-4o-mini"
  apiKeyEnv: "LLM_API_KEY"
  timeoutMs: 15000
agent:
  maxSteps: 5
store:
  path: "./data"
mail:
  pollSeconds: 30
  profile: "Outlook"
  folder: "Inbox"
audit:
  hmacKeyEnv: "AUDIT_HMAC_KEY"
```

Секретов в файле быть не должно — только *имя* переменной окружения
(`apiKeyEnv`, `hmacKeyEnv`); фактическое значение читается из `System.getenv`
в момент старта.

## 6. Пошаговый план по вехам (TDD, red→green, атомарные коммиты)

Каждая веха — минимум 2 коммита: `test: ...` (красный) → `feat: ...`
(зелёный). Смешивать тест и реализацию в одном коммите нельзя.

- **M0 — Maven-скелет.** `pom.xml` (зависимости и плагины из §3), `.gitignore`,
  структура пакетов (пустые package-info или заглушки), `README.md`-заглушка.
  Без тестов — инфраструктурный коммит.
- **M1 — `Msg` + `MailChannel` + `MockMailChannel`.** Тест: `fetchUnread()`
  отдаёт заранее заданные письма; `reply(msg, body)` фиксирует ответ для
  проверки в тесте.
- **M2 — `SeenStore`.** Тест: новый id не помечен → после `markSeen` помечен;
  новый инстанс `SeenStore` над тем же файлом (эмуляция рестарта процесса)
  видит ранее помеченные id.
- **M3 — Инструменты.** `CurrentDatetimeTool` с фиксированным `Clock` →
  детерминированный ISO. `ReminderStore` + `AddReminderTool` → запись
  сохраняется и читается обратно. `FindItemsTool` → находит ранее
  добавленные записи по подстроке.
- **M4 — `LlmClient` контракт + `MockLlmClient`.** Тест: скриптованная
  последовательность ответов отдаётся по порядку вызовов `chat(...)`.
- **M5 — `ToolLoop`.**
  - happy-path: `tool_call` → результат → финальный ответ модели.
  - `maxSteps`: модель никогда не отдаёт финал → цикл корректно
    останавливается на лимите шагов, без исключения.
  - устойчивость: неизвестное имя tool / битый JSON аргументов → цикл не
    падает, ошибка уходит обратно модели как tool-результат.
- **M6 — `AgentService`.** Сборка `MockMailChannel` + `MockLlmClient` +
  `ToolLoop` на 4 golden-письмах из §10 задания (напоминание, список,
  текущая дата, пустое/мусорное письмо). Плюс интеграционная проверка
  идемпотентности: повторный `fetchUnread()` с тем же письмом не порождает
  второй ответ.
- **M7 — `ConfigLoader`.** Тест: YAML → `AppConfig` корректно матчится по
  полям; резолв ключа из env по имени, заданному в конфиге (через
  инжектируемый провайдер env, чтобы не мутировать реальные переменные
  окружения в тесте).
- **M8 — Graceful-фолбэки.** `LlmClient` кидает исключение/таймаут →
  `AgentService` ловит, шлёт письмо-фолбэк и пишет WARN с event-key
  `llm_failed`, не падает. `MailChannel` кидает при обработке одного письма
  (эмуляция COM-ошибки) → WARN, следующее письмо в батче всё равно
  обрабатывается.
- **M9 — `HmacSigner` + `AuditLog`.** Тест: одинаковый вход + ключ →
  одинаковая подпись; цепочка хешей переживает переоткрытие файла (новый
  `AuditLog` над тем же файлом продолжает цепочку); подмена записи в файле
  детектируется при проверке цепочки.
- **M10 — Структурные логи и PII-маскирование.** Тест на `PiiMasker`
  (маскирует адрес/тело); тест end-to-end через тестовый logback-appender,
  что тело письма никогда не попадает в лог при полной обработке письма
  через `AgentService`.
- **M11 — `OutlookMailChannel` (JACOB).** Реализация без юнит-теста (реальный
  COM недоступен на CI) — за интерфейсом `MailChannel`, поэтому `mvn test`
  остаётся зелёным. Плюс чек-лист ручной проверки на живом Outlook (для
  защиты).
- **M12 — `HttpLlmClient` (okhttp).** Юнит-тест на `MockWebServer`
  (test-scope): проверка формы запроса (заголовок авторизации из
  резолвленного env, JSON-тело с `tools`), разбор ответа (`tool_calls` и
  финальный `content`), обработка не-200 статуса и битого JSON.
- **M13 — `Main` + poll-loop wiring.** Сборка всех компонентов по
  `AppConfig`; ручная проверка `java -jar` с mock-конфигом (без реального
  Outlook/LLM).
- **M14 — `mvn package` smoke-run.** Fat-jar собирается и запускается —
  подтверждается реальным выводом команды (verification-before-completion).
- **M15 — Security review.** Само-ревью перед сдачей: grep на секреты в
  коде/git, grep на логирование тела письма/println с ПДн, разбор
  tool-аргументов на инъекции (путь к файлу в `ReminderStore` строится
  только из конфига, не из пользовательского ввода).
- **M16 — Финализация README.md.** Разделы build/run/test + «Как я работал
  с ИИ» (стратегия промптов, что проверяли у модели, что отклонили) +
  экспорт сессии Claude Code.

## 7. Стратегия коммитов

Один коммит = один логический red-или-green шаг. Сообщения: `test: ...` для
падающего теста, `feat: ...` для реализации, которая его зажигает зелёным,
`fix: ...` для точечных правок, `docs: ...` для документации/конфига. Никаких
«AI-dump»-коммитов на сотни файлов разом.

## 8. Тестовая стратегия

`mvn test` должен быть зелёным на машине без Outlook (JACOB исключён из
test-classpath через surefire). Покрытие по списку §5 задания:

- юниты инструментов (M3);
- tool-loop на мок-LLM, включая maxSteps и устойчивость к галлюцинациям (M5);
- канал на `MockMailChannel` (письмо-вход → ожидаемый ответ) (M1, M6);
- загрузка конфига (M7);
- путь фолбэка (M8);
- идемпотентность — одно письмо не обрабатывается дважды (M2, M6).

## 9. Чек-лист готовности (из §11 задания)

- [ ] `mvn package` → fat-jar, запускается
- [ ] `mvn test` зелёный без Outlook
- [ ] `MailChannel`: JACOB-реализация + мок
- [ ] ≥2 инструмента, tool-loop работает на моке
- [ ] идемпотентность (seen) + переживает рестарт
- [ ] конфиг-driven, секреты из env, в git ничего секретного
- [ ] graceful-фолбэк на LLM и COM
- [ ] структурные логи, без ПДн
- [ ] аудит-журнал действий (hash-chain)
- [ ] PLAN.md + экспорт сессии Claude Code + README

## 10. Вне scope (из §6 задания)

Реальный Telegram, Confluence, календарь, DPAPI/cookies, RAG/эмбеддинги, БД
сложнее JSON-файла, мультипользовательность, OAuth/SSO, веб-панель, деплой
сверх fat-jar. Один инстанс, один ящик.

## 11. Допущения

- LLM-эндпоинт/модель/имя env-переменной с ключом — полностью конфиг-driven,
  без хардкода конкретного вендора; формат запроса/ответа — OpenAI-совместимый
  Chat Completions с `tools`/`tool_calls`.
- Package base: `com.miniassistant`.
- Референсного проекта «Коля» на машине нет — паттерны (`HmacSigner`,
  surefire-exclude для jacob) спроектированы заново по описанию из задания,
  без копирования конкретного кода.

## 12. Stretch (опционально, если останется время)

retry/timeout/backoff для LLM; allow/deny-gate инструментов из конфига;
память диалога по отправителю/треду; override конфига через env-переменные;
устойчивость к COM-сбою (reconnect); расширенный аудит. Берём в работу только
после того, как весь чек-лист §9 закрыт.
