ALTER TABLE section
    ADD COLUMN is_executed_as_whole BOOLEAN NOT NULL;

ALTER TABLE module
    ADD COLUMN has_points BOOLEAN NOT NULL;

ALTER TABLE module
    ADD COLUMN is_accepted_separately BOOLEAN NOT NULL;

ALTER TABLE module_score
    ALTER points DROP NOT NULL;
