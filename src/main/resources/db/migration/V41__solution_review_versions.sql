ALTER TABLE project_node_solution_review
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER completed_at;
