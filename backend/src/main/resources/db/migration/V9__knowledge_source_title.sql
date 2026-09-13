ALTER TABLE knowledge_source
  ADD COLUMN title varchar(500);

UPDATE knowledge_source s
   SET title = n.title
  FROM knowledge_node n
 WHERE n.id = s.node_id;

ALTER TABLE knowledge_source
  ALTER COLUMN title SET NOT NULL;
