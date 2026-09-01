-- In-app inbox. Email/SMTP stays a separate channel.
CREATE TABLE user_notification (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    type        VARCHAR(40)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    content     VARCHAR(500),
    project_id  BIGINT,
    task_id     BIGINT,
    actor_id    BIGINT,
    read_at     TIMESTAMP    NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    version     INT          NOT NULL DEFAULT 0,
    CONSTRAINT notification_user_fk FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT notification_project_fk FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT notification_task_fk FOREIGN KEY (task_id) REFERENCES project_task (id),
    CONSTRAINT notification_actor_fk FOREIGN KEY (actor_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_notification_user_unread ON user_notification (user_id, read_at, created_at, deleted);
