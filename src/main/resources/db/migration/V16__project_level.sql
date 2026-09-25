ALTER TABLE project
    ADD COLUMN project_level TINYINT NOT NULL DEFAULT 0 COMMENT '0常规(C) 1重要(B) 2关键(A) 3战略(S)';
