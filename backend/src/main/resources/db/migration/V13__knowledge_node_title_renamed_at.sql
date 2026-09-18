-- 사용자 이름 수정 시각은 일반 updated_at과 분리한다. 기존 노드는 수정 이력이 없어 null이다.
ALTER TABLE knowledge_node
    ADD COLUMN title_renamed_at timestamptz;
