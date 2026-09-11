-- Deep-link node lifecycle inbox events back to the project node.
ALTER TABLE user_notification ADD COLUMN node_id BIGINT NULL;
ALTER TABLE user_notification
    ADD CONSTRAINT notification_node_fk FOREIGN KEY (node_id) REFERENCES project_node (id);
