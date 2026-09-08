-- Subject resolution은 현재 Source의 Summary와 의미가 가까운 기존 Source를 먼저 찾는다.
-- text-embedding-3-small의 dimensions 옵션을 768로 고정해 저장·조회 차원을 일치시킨다.
ALTER TABLE knowledge_source
  ADD COLUMN summary_embedding vector(768),
  ADD COLUMN summary_embedding_model varchar(100),
  ADD COLUMN failure_message varchar(255),
  ADD COLUMN retryable boolean NOT NULL DEFAULT false,
  ADD CONSTRAINT chk_knowledge_source_summary_embedding_pair
    CHECK (
      (summary_embedding IS NULL AND summary_embedding_model IS NULL)
      OR (summary_embedding IS NOT NULL AND summary_embedding_model IS NOT NULL)
    );

-- cosine distance Top-K 조회용. 임베딩이 생긴 Source만 인덱스에 넣는다.
CREATE INDEX idx_knowledge_source_summary_embedding_hnsw
  ON knowledge_source
  USING hnsw (summary_embedding vector_cosine_ops)
  WHERE summary_embedding IS NOT NULL;
