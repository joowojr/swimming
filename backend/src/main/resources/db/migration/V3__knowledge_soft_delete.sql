-- Source와 노드를 지울 때 행을 남긴다. 관계는 그대로 두고 조회에서 삭제된 노드를 거른다.
-- 플래그를 knowledge_source가 아니라 knowledge_node에 두는 이유는, sourceId가 곧 nodeId이고
-- Graph가 knowledge_node를 읽기 때문이다. Source 쪽에만 두면 목록에서 사라진 문서가
-- Graph의 노드 이름 조회에서 되살아난다.

ALTER TABLE knowledge_node
  ADD COLUMN is_deleted boolean NOT NULL DEFAULT false;

-- 모든 읽기 경로가 이 플래그를 거르므로 인덱스에도 넣는다.
DROP INDEX idx_knowledge_node_user_type;

CREATE INDEX idx_knowledge_node_user_type_deleted
  ON knowledge_node (user_id, node_type, is_deleted);

-- 지운 Subject는 중복 판정에서 빠져야 한다. 빼지 않으면 같은 개념을 다시 저장할 때
-- resolution은 삭제된 행을 못 찾아 새로 만들려 하고, 인덱스가 그것을 막아 소화가 실패한다.
DROP INDEX uq_knowledge_node_normalized;

CREATE UNIQUE INDEX uq_knowledge_node_normalized
  ON knowledge_node (user_id, node_type, normalized_title)
  WHERE node_type = 'SUBJECT' AND is_deleted = false;
