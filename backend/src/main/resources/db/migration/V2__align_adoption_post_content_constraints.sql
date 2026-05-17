-- =============================================================================
-- V2__align_adoption_post_content_constraints.sql
-- Align runtime adoption post constraints with the MVP service/API contract.
--
-- Existing API paths already reject titles longer than 200 characters and
-- require non-blank content. This migration makes the database enforce the
-- same boundary for runtime MySQL schemas built from V1.
-- If manually inserted legacy rows violate these constraints, clean them
-- before applying this migration.
-- =============================================================================

ALTER TABLE adoption_posts
    MODIFY title VARCHAR(200) NOT NULL,
    MODIFY content TEXT NOT NULL;
