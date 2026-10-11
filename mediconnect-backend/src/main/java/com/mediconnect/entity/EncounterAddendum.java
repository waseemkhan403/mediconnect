package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A correction or later finding appended to a FINAL encounter by its doctor. Never edited or deleted:
 * the original record plus its addenda is the full history.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "encounter_addenda")
public class EncounterAddendum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "addendum_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @Column(name = "text", nullable = false, length = 2000)
    private String text;

    /** When it was added; part of the record (it shows the correction came later). */
    @Column(name = "added_at", nullable = false)
    private Instant addedAt;
}
