ALTER TABLE project_node_solution_review
    ADD COLUMN reviewer_id BIGINT NULL AFTER review_type;

CREATE INDEX idx_node_solution_review_reviewer
    ON project_node_solution_review (project_id, node_id, reviewer_id);
