package com.shiro.shiroaiagent.agent;


import com.shiro.shiroaiagent.agent.model.AgentStateException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    public abstract boolean think();

    public abstract String act();

    @Override
    public String step() {
        try {
            // 先思考
            boolean shouldAct = think();
            if (!shouldAct) {
                return getConclusion();
            }
            // 再行动
            return act();
        } catch (Exception e) {
            // 记录日常日志
            e.printStackTrace();
            throw new AgentStateException("步骤执行失败：" + e.getMessage());
        }
    }

    /**
     * 无需行动时的收尾内容；子类可重写以返回模型的最终回答
     */
    protected String getConclusion() {
        return "思考完成，无需行动";
    }
}
