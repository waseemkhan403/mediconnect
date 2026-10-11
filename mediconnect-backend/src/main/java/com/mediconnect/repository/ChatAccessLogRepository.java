package com.mediconnect.repository;

import com.mediconnect.entity.ChatAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatAccessLogRepository extends JpaRepository<ChatAccessLog, Long> {

    /**
     * Admin accesses to chats. Null = do not filter. doctorId / ownerId are forced by the service so the two
     * parties only see the log of their own chats.
     */
    @Query(value = """
            SELECT l FROM ChatAccessLog l
            JOIN FETCH l.thread t
            JOIN FETCH t.encounter e
            JOIN FETCH e.appointment a
            JOIN FETCH a.patient p
            JOIN FETCH a.doctor d
            JOIN FETCH d.staff
            LEFT JOIN FETCH l.user u
            LEFT JOIN FETCH u.staff
            LEFT JOIN p.owner o
            WHERE (CAST(:threadId AS Long) IS NULL OR t.id = :threadId)
              AND (CAST(:doctorId AS Long) IS NULL OR d.id = :doctorId)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
            """,
            countQuery = """
            SELECT COUNT(l) FROM ChatAccessLog l
            JOIN l.thread t
            JOIN t.encounter e
            JOIN e.appointment a
            JOIN a.patient p
            LEFT JOIN p.owner o
            WHERE (CAST(:threadId AS Long) IS NULL OR t.id = :threadId)
              AND (CAST(:doctorId AS Long) IS NULL OR a.doctor.id = :doctorId)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
            """)
    Page<ChatAccessLog> findAllWithFilters(Long threadId, Long doctorId, Long ownerId, Pageable pageable);
}
