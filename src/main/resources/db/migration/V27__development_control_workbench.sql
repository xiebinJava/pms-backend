CREATE TABLE project_node_development_baseline (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id       BIGINT       NOT NULL,
    node_id          BIGINT       NOT NULL,
    current_iteration VARCHAR(120),
    version          INT          NOT NULL DEFAULT 0,
    created_by       BIGINT,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_development_baseline_project_node (project_id, node_id),
    INDEX idx_node_development_baseline_node (node_id)
);

CREATE TABLE project_node_development_topic (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id          BIGINT       NOT NULL,
    node_id             BIGINT       NOT NULL,
    title               VARCHAR(200) NOT NULL,
    owner_id            BIGINT,
    iteration           VARCHAR(120),
    latest_build_version VARCHAR(120),
    test_status         VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED',
    sort                INT          NOT NULL DEFAULT 0,
    created_by          BIGINT,
    created_at          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_node_development_topic_node (project_id, node_id, sort),
    INDEX idx_node_development_topic_owner (project_id, owner_id)
);

CREATE TABLE project_node_development_story (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id   BIGINT       NOT NULL,
    node_id      BIGINT       NOT NULL,
    topic_id     BIGINT       NOT NULL,
    title        VARCHAR(300) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED',
    progress     INT          NOT NULL DEFAULT 0,
    story_points INT          NOT NULL DEFAULT 0,
    blocker      VARCHAR(500),
    sort         INT          NOT NULL DEFAULT 0,
    created_by   BIGINT,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_node_development_story_topic (project_id, node_id, topic_id, sort),
    INDEX idx_node_development_story_status (project_id, node_id, status)
);
