ALTER TABLE project_node_plan_baseline
    ADD COLUMN solution_decision_version INT NULL COMMENT '当前计划基线对应的已确认方案决策版本';
