CREATE TABLE pms_topic_research_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    file_key VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_topic_research_node (topic_id, node_id),
    CONSTRAINT fk_topic_research_node FOREIGN KEY (node_id) REFERENCES pms_development_item_workflow_node(id)
);
