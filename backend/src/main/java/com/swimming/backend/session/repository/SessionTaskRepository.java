package com.swimming.backend.session.repository;

import com.swimming.backend.session.repository.entity.SessionTaskEntity;
import com.swimming.backend.session.repository.entity.SessionTaskId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SessionTaskRepository extends JpaRepository<SessionTaskEntity, SessionTaskId> {

    @Query("""
            select sessionTask.id.taskId
            from SessionTaskEntity sessionTask
            where sessionTask.session.id = :sessionId
            """)
    List<Long> findTaskIdsBySessionId(@Param("sessionId") Long sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update SessionTaskEntity sessionTask
            set sessionTask.isCompleted = true,
                sessionTask.updatedAt = instant
            where sessionTask.session.id = :sessionId
              and sessionTask.id.taskId in :taskIds
            """)
    int completeAll(
            @Param("sessionId") Long sessionId,
            @Param("taskIds") List<Long> taskIds
    );
}
