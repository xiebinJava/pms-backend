ALTER TABLE project_node_acceptance_baseline
    ADD COLUMN requirement_baseline_version INT NULL COMMENT '验收对应的需求范围基线版本';

ALTER TABLE project_node_acceptance_defect
    ADD COLUMN source_defect_id BIGINT NULL COMMENT '未来缺陷管理模块主键';

CREATE INDEX idx_node_acceptance_defect_source_id
    ON project_node_acceptance_defect (source_defect_id);
