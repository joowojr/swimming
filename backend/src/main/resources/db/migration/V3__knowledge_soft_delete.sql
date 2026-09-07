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

-- 폴더 삭제 가드. 링크가 남은 폴더를 지우면 knowledge_source가 없는 폴더를 가리킨 채 남는다.
-- knowledge_source.folder_id는 NOT NULL이라 링크를 떼어 둘 자리도 없다.
-- folder가 knowledge에 물어보면 의존 방향이 깨지므로, folder가 자기 컬럼만 보고 판단한다.
ALTER TABLE folders
  ADD COLUMN has_source boolean NOT NULL DEFAULT false;

-- 이미 링크를 모아 둔 폴더가 있으면 켠 채로 시작한다. 꺼진 채로 두면 가드가 통과해
-- 링크가 남은 폴더가 지워진다.
UPDATE folders f
   SET has_source = true
 WHERE EXISTS (
         SELECT 1
           FROM knowledge_source s
           JOIN knowledge_node n ON n.id = s.node_id
          WHERE s.folder_id = f.id
            AND n.is_deleted = false
       );

-- 링크를 읽었는지. boolean이 아니라 시각으로 둔다. 컬럼 하나가 여부와 시점을 함께 담고,
-- "이번 주 읽은 링크 6개" 같은 축적 표현과 최근 읽은 순 정렬을 나중에 그대로 쓸 수 있다.
-- knowledge_node가 아니라 knowledge_source에 두는 이유는 읽을 수 있는 것이 SOURCE 뿐이고,
-- Graph 조회가 읽음 여부를 거를 일이 없기 때문이다.
ALTER TABLE knowledge_source
  ADD COLUMN read_at timestamptz;
