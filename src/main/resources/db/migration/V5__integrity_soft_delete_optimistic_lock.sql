-- Enterprise integrity hardening: soft-delete markers, optimistic locks,
-- uniqueness and relational constraints. No rows are removed or rewritten.

ALTER TABLE sys_user ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE project ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE project_node ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE project_task ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE project_milestone ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE project_member ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE project_follower ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE project_comment ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_org_unit ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_role ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE sys_user ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE project ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE project_node ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE project_task ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE project_milestone ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE project_member ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE project_follower ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE project_comment ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE sys_org_unit ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE sys_user_position ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE sys_role ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE sys_user_role ADD COLUMN version INT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uk_project_code ON project (code);
CREATE UNIQUE INDEX uk_project_node_key ON project_node (project_id, node_key);
CREATE INDEX idx_user_deleted ON sys_user (deleted, status);
CREATE INDEX idx_project_deleted ON project (deleted, status);
CREATE INDEX idx_task_deleted ON project_task (deleted, project_id, status);
CREATE INDEX idx_milestone_deleted ON project_milestone (deleted, project_id, status);
CREATE INDEX idx_node_deleted ON project_node (deleted, project_id, sort);
CREATE INDEX idx_org_unit_deleted ON sys_org_unit (deleted, status, sort);
CREATE INDEX idx_role_deleted ON sys_role (deleted, enabled);

ALTER TABLE sys_org_unit
    ADD CONSTRAINT org_unit_parent_fk FOREIGN KEY (parent_id) REFERENCES sys_org_unit (id);
ALTER TABLE sys_org_unit
    ADD CONSTRAINT org_unit_type_fk FOREIGN KEY (type_id) REFERENCES sys_org_unit_type (id);
ALTER TABLE sys_org_unit
    ADD CONSTRAINT org_unit_leader_fk FOREIGN KEY (leader_user_id) REFERENCES sys_user (id);
ALTER TABLE project
    ADD CONSTRAINT project_owner_fk FOREIGN KEY (owner_id) REFERENCES sys_user (id);
ALTER TABLE project
    ADD CONSTRAINT project_creator_fk FOREIGN KEY (created_by) REFERENCES sys_user (id);
ALTER TABLE project
    ADD CONSTRAINT project_manager_fk FOREIGN KEY (project_manager_id) REFERENCES sys_user (id);
ALTER TABLE project
    ADD CONSTRAINT project_org_unit_fk FOREIGN KEY (org_unit_id) REFERENCES sys_org_unit (id);
ALTER TABLE project_node
    ADD CONSTRAINT node_project_fk FOREIGN KEY (project_id) REFERENCES project (id);
ALTER TABLE project_node
    ADD CONSTRAINT node_owner_fk FOREIGN KEY (owner_id) REFERENCES sys_user (id);
ALTER TABLE project_task
    ADD CONSTRAINT task_project_fk FOREIGN KEY (project_id) REFERENCES project (id);
ALTER TABLE project_task
    ADD CONSTRAINT task_node_fk FOREIGN KEY (node_id) REFERENCES project_node (id);
ALTER TABLE project_task
    ADD CONSTRAINT task_parent_fk FOREIGN KEY (parent_id) REFERENCES project_task (id);
ALTER TABLE project_task
    ADD CONSTRAINT task_assignee_fk FOREIGN KEY (assignee_id) REFERENCES sys_user (id);
ALTER TABLE project_task
    ADD CONSTRAINT task_milestone_fk FOREIGN KEY (milestone_id) REFERENCES project_milestone (id);
ALTER TABLE project_task
    ADD CONSTRAINT task_creator_fk FOREIGN KEY (created_by) REFERENCES sys_user (id);
ALTER TABLE project_milestone
    ADD CONSTRAINT milestone_project_fk FOREIGN KEY (project_id) REFERENCES project (id);
ALTER TABLE project_milestone
    ADD CONSTRAINT milestone_creator_fk FOREIGN KEY (created_by) REFERENCES sys_user (id);
ALTER TABLE project_member
    ADD CONSTRAINT member_project_fk FOREIGN KEY (project_id) REFERENCES project (id);
ALTER TABLE project_member
    ADD CONSTRAINT member_user_fk FOREIGN KEY (user_id) REFERENCES sys_user (id);
ALTER TABLE project_follower
    ADD CONSTRAINT follower_project_fk FOREIGN KEY (project_id) REFERENCES project (id);
ALTER TABLE project_follower
    ADD CONSTRAINT follower_user_fk FOREIGN KEY (user_id) REFERENCES sys_user (id);
ALTER TABLE project_comment
    ADD CONSTRAINT comment_project_fk FOREIGN KEY (project_id) REFERENCES project (id);
ALTER TABLE project_comment
    ADD CONSTRAINT comment_task_fk FOREIGN KEY (task_id) REFERENCES project_task (id);
ALTER TABLE project_comment
    ADD CONSTRAINT comment_user_fk FOREIGN KEY (user_id) REFERENCES sys_user (id);
ALTER TABLE project_lifecycle_log
    ADD CONSTRAINT lifecycle_project_fk FOREIGN KEY (project_id) REFERENCES project (id);
ALTER TABLE project_lifecycle_log
    ADD CONSTRAINT lifecycle_operator_fk FOREIGN KEY (operator_id) REFERENCES sys_user (id);
ALTER TABLE sys_user_position
    ADD CONSTRAINT user_position_user_fk FOREIGN KEY (user_id) REFERENCES sys_user (id);
ALTER TABLE sys_user_position
    ADD CONSTRAINT user_position_org_fk FOREIGN KEY (org_unit_id) REFERENCES sys_org_unit (id);
ALTER TABLE sys_user_position
    ADD CONSTRAINT user_position_position_fk FOREIGN KEY (position_id) REFERENCES sys_position (id);
ALTER TABLE sys_user_position
    ADD CONSTRAINT user_position_manager_fk FOREIGN KEY (manager_user_id) REFERENCES sys_user (id);
ALTER TABLE sys_role_permission
    ADD CONSTRAINT role_permission_role_fk FOREIGN KEY (role_id) REFERENCES sys_role (id);
ALTER TABLE sys_role_permission
    ADD CONSTRAINT role_permission_permission_fk FOREIGN KEY (permission_id) REFERENCES sys_permission (id);
ALTER TABLE sys_user_role
    ADD CONSTRAINT user_role_user_fk FOREIGN KEY (user_id) REFERENCES sys_user (id);
ALTER TABLE sys_user_role
    ADD CONSTRAINT user_role_role_fk FOREIGN KEY (role_id) REFERENCES sys_role (id);
ALTER TABLE sys_user_role
    ADD CONSTRAINT user_role_scope_org_fk FOREIGN KEY (scope_org_unit_id) REFERENCES sys_org_unit (id);
ALTER TABLE sys_role_org_scope
    ADD CONSTRAINT role_scope_role_fk FOREIGN KEY (role_id) REFERENCES sys_role (id);
ALTER TABLE sys_role_org_scope
    ADD CONSTRAINT role_scope_org_fk FOREIGN KEY (org_unit_id) REFERENCES sys_org_unit (id);
ALTER TABLE sys_invitation
    ADD CONSTRAINT invitation_user_fk FOREIGN KEY (user_id) REFERENCES sys_user (id);
ALTER TABLE sys_invitation
    ADD CONSTRAINT invitation_creator_fk FOREIGN KEY (created_by) REFERENCES sys_user (id);
ALTER TABLE sys_auth_session
    ADD CONSTRAINT auth_session_user_fk FOREIGN KEY (user_id) REFERENCES sys_user (id);
ALTER TABLE sys_password_reset_token
    ADD CONSTRAINT reset_token_user_fk FOREIGN KEY (user_id) REFERENCES sys_user (id);
ALTER TABLE sys_login_log
    ADD CONSTRAINT login_log_user_fk FOREIGN KEY (user_id) REFERENCES sys_user (id);
ALTER TABLE sys_operation_log
    ADD CONSTRAINT operation_log_operator_fk FOREIGN KEY (operator_id) REFERENCES sys_user (id);
ALTER TABLE sys_import_job
    ADD CONSTRAINT import_job_creator_fk FOREIGN KEY (created_by) REFERENCES sys_user (id);
