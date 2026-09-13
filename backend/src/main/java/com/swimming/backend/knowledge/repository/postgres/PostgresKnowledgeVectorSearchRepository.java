package com.swimming.backend.knowledge.repository.postgres;

import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.repository.KnowledgeVectorSearchRepository;
import com.swimming.backend.knowledge.repository.SimilarSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresKnowledgeVectorSearchRepository implements KnowledgeVectorSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<KnowledgeNode> findSimilarSubjects(
            Long userId,
            float[] titleEmbedding,
            String embeddingModel,
            int limit
    ) {
        return jdbcTemplate.query(
                """
                select n.id, n.user_id, n.node_type, n.title, n.description,
                       n.is_deleted, n.created_at, n.updated_at
                  from knowledge_node n
                 where n.user_id = ?
                   and n.node_type = 'SUBJECT'
                   and n.is_deleted = false
                   and n.title_embedding is not null
                   and n.title_embedding_model = ?
                 order by n.title_embedding <=> cast(? as vector)
                 limit ?
                """,
                (resultSet, rowNumber) -> KnowledgeNode.restore(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getLong("user_id"),
                        NodeType.valueOf(resultSet.getString("node_type")),
                        resultSet.getString("title"),
                        resultSet.getString("description"),
                        resultSet.getBoolean("is_deleted"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()
                ),
                userId, embeddingModel, vectorLiteral(titleEmbedding), limit
        );
    }

    @Override
    public List<SimilarSource> findSimilarSources(
            Long userId,
            UUID excludedSourceId,
            float[] summaryEmbedding,
            String embeddingModel,
            int limit
    ) {
        return jdbcTemplate.query(
                """
                select s.node_id, s.title,
                       s.summary_embedding <=> cast(? as vector) as distance
                  from knowledge_source s
                  join knowledge_node n on n.id = s.node_id
                 where n.user_id = ?
                   and n.is_deleted = false
                   and s.node_id <> ?
                   and s.processing_status = 'COMPLETED'
                   and s.summary_embedding is not null
                   and s.summary_embedding_model = ?
                 order by s.summary_embedding <=> cast(? as vector)
                 limit ?
                """,
                (resultSet, rowNumber) -> new SimilarSource(
                        resultSet.getObject("node_id", UUID.class),
                        resultSet.getString("title"),
                        resultSet.getDouble("distance")
                ),
                vectorLiteral(summaryEmbedding), userId, excludedSourceId, embeddingModel,
                vectorLiteral(summaryEmbedding), limit
        );
    }

    private String vectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(Float.toString(embedding[index]));
        }
        return literal.append(']').toString();
    }
}
