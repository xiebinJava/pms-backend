ALTER TABLE project_node_solution_package
    DROP COLUMN package_version;

ALTER TABLE project_node_solution_package
    DROP COLUMN summary;

ALTER TABLE project_node_solution_package
    DROP COLUMN scope_coverage;

ALTER TABLE project_node_solution_package
    DROP COLUMN rollout_premise;

ALTER TABLE project_node_solution_decision
    DROP COLUMN reason;
