-- 할 일에 저장한 링크를 붙인다. 붙는 방향이 하나뿐이라 연결 자체가 곧 행의 정체성이고,
-- 같은 쌍이 두 번 있을 수 없으므로 대리키 없이 복합 PK를 쓴다.
CREATE TABLE task_sources (
  task_id bigint NOT NULL,
  source_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (task_id, source_id)
);

-- PK가 "이 할 일의 문서들"을 해결한다. 반대 방향(이 문서가 붙은 할 일, 문서 삭제 시 정리)은 따로 필요하다.
CREATE INDEX idx_task_sources_source ON task_sources (source_id);

ALTER TABLE task_sources ADD
  FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE;

-- knowledge는 soft delete라 CASCADE가 발동할 일이 없다. 존재하지 않는 문서를 가리키는 것만 막는다.
-- 지워진 문서의 연결을 실제로 걷어내는 것은 조회 필터와 삭제 이벤트가 맡는다.
ALTER TABLE task_sources ADD
  FOREIGN KEY (source_id) REFERENCES knowledge_source (node_id);
