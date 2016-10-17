ALTER TABLE section RENAME COLUMN executed_as_whole TO is_executed_as_whole;
ALTER TABLE module RENAME COLUMN points TO has_points;
ALTER TABLE module RENAME COLUMN accepted_separately TO is_accepted_separately;
