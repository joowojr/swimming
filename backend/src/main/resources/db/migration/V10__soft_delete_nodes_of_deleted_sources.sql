-- 과거에 Source만 soft delete되어 남은 종속 노드를 현재 삭제 규칙에 맞춘다.
-- 관계 행은 이력으로 남기고 knowledge_node의 삭제 상태만 갱신한다.

-- Topic은 Source마다 새로 생성되므로, 삭제된 Source가 SUPPORTS하는 Topic도 함께 지운다.
UPDATE knowledge_node topic
   SET is_deleted = true,
       updated_at = CURRENT_TIMESTAMP
 WHERE topic.node_type = 'TOPIC'
   AND topic.is_deleted = false
   AND EXISTS (
         SELECT 1
           FROM knowledge_relation relation
           JOIN knowledge_node source
             ON source.id = relation.from_node_id
           JOIN knowledge_source source_detail
             ON source_detail.node_id = source.id
          WHERE relation.to_node_id = topic.id
            AND relation.relation_type = 'SUPPORTS'
            AND source.node_type = 'SOURCE'
            AND source.is_deleted = true
       );

-- Subject는 여러 Source가 공유할 수 있다. 삭제된 Source가 ABOUT하던 Subject 중
-- 활성 Source가 하나도 참조하지 않는 고아 Subject만 지운다.
UPDATE knowledge_node subject
   SET is_deleted = true,
       updated_at = CURRENT_TIMESTAMP
 WHERE subject.node_type = 'SUBJECT'
   AND subject.is_deleted = false
   AND EXISTS (
         SELECT 1
           FROM knowledge_relation deleted_reference
           JOIN knowledge_node deleted_source
             ON deleted_source.id = deleted_reference.from_node_id
           JOIN knowledge_source deleted_source_detail
             ON deleted_source_detail.node_id = deleted_source.id
          WHERE deleted_reference.to_node_id = subject.id
            AND deleted_reference.relation_type = 'ABOUT'
            AND deleted_source.node_type = 'SOURCE'
            AND deleted_source.is_deleted = true
       )
   AND NOT EXISTS (
         SELECT 1
           FROM knowledge_relation active_reference
           JOIN knowledge_node active_source
             ON active_source.id = active_reference.from_node_id
           JOIN knowledge_source active_source_detail
             ON active_source_detail.node_id = active_source.id
          WHERE active_reference.to_node_id = subject.id
            AND active_reference.relation_type = 'ABOUT'
            AND active_source.node_type = 'SOURCE'
            AND active_source.is_deleted = false
       );
