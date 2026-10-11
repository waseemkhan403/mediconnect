package com.mediconnect.repository;

import com.mediconnect.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Newest first, older than the message id "before" (null = from the latest). Cursor paging instead of
     * page numbers: new messages arriving while the user scrolls back would shift page numbers and repeat rows.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.thread.id = :threadId
              AND (CAST(:before AS Long) IS NULL OR m.id < :before)
            ORDER BY m.id DESC
            """)
    List<ChatMessage> findPage(Long threadId, Long before, Pageable pageable);

    @Query("SELECT COALESCE(MAX(m.id), 0) FROM ChatMessage m WHERE m.thread.id = :threadId")
    long findLatestId(Long threadId);

    /** Unread for the patient side: the doctor's messages after the patient's read marker. Rows: [threadId, count]. */
    @Query("""
            SELECT m.thread.id, COUNT(m) FROM ChatMessage m
            WHERE m.thread.id IN :threadIds
              AND m.senderRole = 'DOCTOR'
              AND m.id > m.thread.patientLastReadId
            GROUP BY m.thread.id
            """)
    List<Object[]> countUnreadForPatient(Collection<Long> threadIds);

    /** Unread for the doctor side: the patient's messages after the doctor's read marker. Rows: [threadId, count]. */
    @Query("""
            SELECT m.thread.id, COUNT(m) FROM ChatMessage m
            WHERE m.thread.id IN :threadIds
              AND m.senderRole = 'PATIENT'
              AND m.id > m.thread.doctorLastReadId
            GROUP BY m.thread.id
            """)
    List<Object[]> countUnreadForDoctor(Collection<Long> threadIds);
}
