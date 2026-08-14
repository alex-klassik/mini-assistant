package com.miniassistant.config;

/** Корень конфигурации приложения - один-в-один секции YAML-файла из PLAN.md §5. */
public final class AppConfig {

    private LlmConfig llm;
    private AgentConfig agent;
    private StoreConfig store;
    private MailConfig mail;
    private AuditConfig audit;

    public LlmConfig getLlm() {
        return llm;
    }

    public void setLlm(LlmConfig llm) {
        this.llm = llm;
    }

    public AgentConfig getAgent() {
        return agent;
    }

    public void setAgent(AgentConfig agent) {
        this.agent = agent;
    }

    public StoreConfig getStore() {
        return store;
    }

    public void setStore(StoreConfig store) {
        this.store = store;
    }

    public MailConfig getMail() {
        return mail;
    }

    public void setMail(MailConfig mail) {
        this.mail = mail;
    }

    public AuditConfig getAudit() {
        return audit;
    }

    public void setAudit(AuditConfig audit) {
        this.audit = audit;
    }
}
