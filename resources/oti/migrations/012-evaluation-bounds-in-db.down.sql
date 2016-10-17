ALTER TABLE section
    DROP COLUMN is_executed_as_whole;

ALTER TABLE module
    DROP COLUMN has_points;

ALTER TABLE module
    DROP COLUMN is_accepted_separately;

ALTER TABLE module_score
    ALTER points SET NOT NULL;
