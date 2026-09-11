-- Subject title은 생성 시 한 번 임베딩하고 이후 resolution에서 재사용한다.
ALTER TABLE knowledge_node
  ADD COLUMN title_embedding vector(768),
  ADD COLUMN title_embedding_model varchar(100),
  ADD CONSTRAINT chk_knowledge_node_subject_title_embedding_pair
    CHECK (
      (title_embedding IS NULL AND title_embedding_model IS NULL)
      OR (
        node_type = 'SUBJECT'
        AND title_embedding IS NOT NULL
        AND title_embedding_model IS NOT NULL
      )
    );

-- 사용자별 Subject cosine distance Top-K 조회용.
CREATE INDEX idx_knowledge_node_subject_title_embedding_hnsw
  ON knowledge_node
  USING hnsw (title_embedding vector_cosine_ops)
  WHERE node_type = 'SUBJECT'
    AND is_deleted = false
    AND title_embedding IS NOT NULL;
