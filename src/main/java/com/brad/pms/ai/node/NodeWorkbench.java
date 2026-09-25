package com.brad.pms.ai.node;

import java.util.Map;

/**
 * One node workbench (需求范围、方案设计、计划资源风险、开发测试、验收、发布、
 * 价值验证、知识沉淀、自定义字段) exposed to the PMS Agent as a field patch.
 *
 * <p>Every PMS workbench already reads as one document and saves as one
 * replacement command, so the Agent surface stays a single table of adapters
 * instead of one hand-written command per workbench field.</p>
 */
public interface NodeWorkbench {

    String key();

    String label();

    /**
     * Whether the workbench defines its own field keys at runtime (workflow
     * custom fields). Dynamic workbenches validate their keys in the service
     * instead of against a fixed field list.
     */
    default boolean dynamicFields() {
        return false;
    }

    /** Current document plus the labels and versions the caller may patch. */
    WorkbenchSnapshot read(Long projectId, Long nodeId);

    /** Applies the patch and returns the refreshed document. */
    Object write(Long projectId, Long nodeId, Map<String, Object> patch);

    @FunctionalInterface
    interface Saver {
        Object save(Long projectId, Long nodeId, Object command);
    }
}
