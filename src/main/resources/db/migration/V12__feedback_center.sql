CREATE TABLE IF NOT EXISTS feedback_ticket (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_no VARCHAR(40) NOT NULL,
    title VARCHAR(160) NOT NULL,
    content VARCHAR(5000) NOT NULL,
    feedback_type VARCHAR(32) NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_TRIAGE',
    project_id BIGINT NULL,
    task_id BIGINT NULL,
    node_id BIGINT NULL,
    context_module VARCHAR(80) NULL,
    source_url VARCHAR(1000) NULL,
    reporter_id BIGINT NOT NULL,
    assignee_id BIGINT NULL,
    resolution_note VARCHAR(2000) NULL,
    client_request_id VARCHAR(80) NULL,
    version INT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT feedback_ticket_ticket_no_uk UNIQUE (ticket_no),
    CONSTRAINT feedback_ticket_reporter_fk FOREIGN KEY (reporter_id) REFERENCES sys_user(id),
    CONSTRAINT feedback_ticket_assignee_fk FOREIGN KEY (assignee_id) REFERENCES sys_user(id),
    CONSTRAINT feedback_ticket_project_fk FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT feedback_ticket_task_fk FOREIGN KEY (task_id) REFERENCES project_task(id),
    CONSTRAINT feedback_ticket_node_fk FOREIGN KEY (node_id) REFERENCES project_node(id)
);

CREATE UNIQUE INDEX feedback_ticket_reporter_request_uk
    ON feedback_ticket (reporter_id, client_request_id);
CREATE INDEX feedback_ticket_status_created_idx
    ON feedback_ticket (status, created_at);
CREATE INDEX feedback_ticket_assignee_status_idx
    ON feedback_ticket (assignee_id, status);
CREATE INDEX feedback_ticket_project_idx
    ON feedback_ticket (project_id, created_at);
CREATE INDEX feedback_ticket_reporter_created_idx
    ON feedback_ticket (reporter_id, created_at, id);

CREATE TABLE IF NOT EXISTS feedback_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NULL,
    from_priority VARCHAR(16) NULL,
    to_priority VARCHAR(16) NULL,
    from_assignee_id BIGINT NULL,
    to_assignee_id BIGINT NULL,
    note VARCHAR(2000) NULL,
    operator_id BIGINT NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT feedback_history_ticket_fk FOREIGN KEY (ticket_id) REFERENCES feedback_ticket(id),
    CONSTRAINT feedback_history_operator_fk FOREIGN KEY (operator_id) REFERENCES sys_user(id)
);

CREATE INDEX feedback_history_ticket_created_idx
    ON feedback_history (ticket_id, created_at);
