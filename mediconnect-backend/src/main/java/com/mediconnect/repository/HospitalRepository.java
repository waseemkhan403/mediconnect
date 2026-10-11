package com.mediconnect.repository;

import com.mediconnect.entity.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HospitalRepository extends JpaRepository<Hospital, Long> {

    /** There is exactly one hospital; its id is fixed (see Hospital.SINGLETON_ID). */
    default Optional<Hospital> findSingleton() {
        return findById(Hospital.SINGLETON_ID);
    }
}
