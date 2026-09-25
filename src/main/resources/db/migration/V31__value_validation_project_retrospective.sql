CREATE TABLE project_node_value_review (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id               BIGINT       NOT NULL,
    node_id                  BIGINT       NOT NULL,
    result_status            VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待填写 ACHIEVED已达成 PARTIAL部分达成 NOT_ACHIEVED未达成',
    actual_result            VARCHAR(4000),
    retrospective_conclusion VARCHAR(4000),
    follow_up_actions        VARCHAR(2000),
    version                  INT          NOT NULL DEFAULT 0,
    created_by               BIGINT,
    created_at               TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_value_review_project_node (project_id, node_id),
    INDEX idx_node_value_review_node (node_id)
);
