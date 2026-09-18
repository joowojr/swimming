ALTER TABLE folders ADD COLUMN source_count bigint NOT NULL DEFAULT 0;

UPDATE folders f
SET source_count = (
    SELECT count(*)
    FROM knowledge_source s
    JOIN knowledge_node n ON n.id = s.node_id
    WHERE s.folder_id = f.id AND n.user_id = f.user_id AND n.is_deleted = false
);

ALTER TABLE folders ADD CONSTRAINT ck_folder_source_count CHECK (source_count >= 0);
ALTER TABLE folders DROP COLUMN has_source;
