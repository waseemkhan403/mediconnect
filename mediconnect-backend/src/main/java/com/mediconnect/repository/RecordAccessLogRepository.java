package com.mediconnect.repository;

import com.mediconnect.entity.RecordAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RecordAccessLogRepository extends JpaRepository<RecordAccessLog, Long> {

    /** Who read which record. Null = do not filter; ownerId limits a patient login to its own records. */
    @Query(value = """
            SELECT l FROM RecordAccessLog l
            JOIN FETCH l.record r
            JOIN FETCH r.patient p
            LEFT JOIN FETCH l.user u
            LEFT JOIN FETCH u.staff
            LEFT JOIN p.owner o
            WHERE (CAST(:recordId AS Long) IS NULL OR r.id = :recordId)
              AND (CAST(:patientId AS Long) IS NULL OR p.id = :patientId)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
            """,
            countQuery = """
            SELECT COUNT(l) FROM RecordAccessLog l
            JOIN l.record r
            JOIN r.patient p
            LEFT JOIN p.owner o
            WHERE (CAST(:recordId AS Long) IS NULL OR r.id = :recordId)
              AND (CAST(:patientId AS Long) IS NULL OR p.id = :patientId)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
            """)
    Page<RecordAccessLog> findAllWithFilters(Long recordId, Long patientId, Long ownerId, Pageable pageable);
}
