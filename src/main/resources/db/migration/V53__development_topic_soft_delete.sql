ALTER TABLE project_node_development_topic
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE AFTER test_status;
