package com.brad.pms.workflow;

import java.util.List;

public final class BuiltInWorkflowTemplate {
    private BuiltInWorkflowTemplate() { }

    public static WorkflowTemplateDefinition compatibilityDefinition() {
        return new WorkflowTemplateDefinition(1, List.of(
                node("kickoff", "项目立项与启动", "判断项目是否值得投入，确认目标、范围与资源授权，提交立项材料并完成审批。",
                        "项目章程（目标、范围与成功标准）、项目阶段计划（WBS）", "项目发起人、业务负责人、技术负责人、项目经理、审批人", "project-basic-info"),
                node("requirement", "需求澄清与范围基线", "澄清业务需求，汇总各方需求并去重，形成范围、优先级与验收基线，与业务确认做与不做。",
                        "需求基线、验收标准与追踪矩阵", "产品负责人、业务代表、项目经理", "requirement-scope"),
                node("design", "方案设计、评审与决策", "验证产品与技术方案，完成关键方案决策，业务、产品、技术和测试共同评审。",
                        "业务与产品方案、技术方案与测试策略、评审结论与决策记录", "产品负责人、项目经理、技术负责人、产品经理、测试负责人、业务代表", "solution-design"),
                node("plan", "计划、资源与风险基线", "建立进度、资源、质量与风险基线，规划迭代计划与上线时间，拆解专题与任务并确认人员与依赖。",
                        "WBS 与项目计划、范围及优先级清单、资源及依赖清单、风险登记册", "项目经理、技术负责人、产品负责人、测试负责人、运维负责人", "plan-resource-risk"),
                node("develop", "开发测试与项目控制", "按迭代完成开发、代码评审、测试与修复，定期更新进度，跟踪问题、风险与跨团队依赖。",
                        "可验证版本、进度/风险/变更记录", "项目经理、开发团队、测试团队、技术负责人、运维负责人", "development-control"),
                node("acceptance", "业务验收与缺陷闭环", "业务人员按照真实场景完成验收，整理验收用例，准备 UAT 环境，问题记录、分派并跟踪修复。",
                        "测试报告与缺陷清单、缺陷关闭及遗留项清单、业务验收结论", "业务负责人、产品负责人、项目经理、测试负责人", "business-acceptance"),
                node("release", "发布决策与运营交接", "完成上线决策与运营交接，记录发布包、配置与回滚预案，确认监控、告警与值守。",
                        "发布记录与回滚预案、可交付版本与代码库、运维交接清单", "项目经理、技术负责人、运维负责人、业务负责人", "release-handover"),
                node("review", "价值验证与项目复盘", "对照立项目标验证项目价值与系统使用情况，复盘延期、返工与沟通问题，回顾工期、质量、成本与团队投入。",
                        "项目价值评估、项目复盘报告", "项目经理、业务负责人、技术负责人、核心项目成员", "value-review"),
                node("knowledge", "知识沉淀与标准改进", "沉淀项目经验与管理标准，更新项目流程和常用检查项，整理可复用的模板、清单与案例，推动改进行动落地。",
                        "项目知识库、标准模板与案例、改进行动清单、流程及检查清单更新", "项目经理、PMO、技术负责人、知识资产负责人", "knowledge-standard")
        ));
    }

    private static WorkflowNodeDefinition node(String key, String name, String description, String deliverable,
                                               String roles, String component) {
        boolean projectBasicInfo = "project-basic-info".equals(component);
        return new WorkflowNodeDefinition(key, name, description, deliverable, roles,
                List.of(component), List.of(), projectBasicInfo,
                projectBasicInfo ? compatibilityProjectFields() : List.of());
    }

    public static List<WorkflowProjectFieldDefinition> compatibilityProjectFields() {
        return List.of(
                new WorkflowProjectFieldDefinition("description", "项目描述", true, true),
                new WorkflowProjectFieldDefinition("priority", "优先级", true, true),
                new WorkflowProjectFieldDefinition("projectLevel", "项目等级", true, false),
                new WorkflowProjectFieldDefinition("schedule", "项目排期", true, true),
                new WorkflowProjectFieldDefinition("businessLine", "业务线", true, false),
                new WorkflowProjectFieldDefinition("projectManager", "项目经理", true, true),
                new WorkflowProjectFieldDefinition("projectMembers", "项目成员", true, true),
                new WorkflowProjectFieldDefinition("followers", "关注人", true, false));
    }
}
