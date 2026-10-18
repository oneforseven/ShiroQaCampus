package com.shiro.shiroaiagent.agent.model;

/**
 * Agent 状态不合法时抛出。
 */
public class AgentStateException extends RuntimeException {

    public AgentStateException() {
        super();
    }

    public AgentStateException(String message) {
        super(message);
    }

    public AgentStateException(String message, Throwable cause) {
        super(message, cause);
    }

    public AgentStateException(Throwable cause) {
        super(cause);
    }
}