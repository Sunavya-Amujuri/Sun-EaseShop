package com.telusko.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * A one-time code emailed to confirm ownership of an address.
 * <p>
 * The code itself is never stored - {@link #token} holds a BCrypt hash of it. A six digit code is
 * a credential, and anyone able to read this table would otherwise be able to activate any
 * pending account.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // BCrypt hash of the emailed code. Not unique: two users can legitimately be issued the
    // same six digits, and BCrypt salts make the hashes differ anyway.
    @Column(nullable = false, length = 120)
    private String token;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    @Column(nullable = false)
    private Long expiresAt;

    // Both columns carry a database default. ddl-auto=update cannot add a NOT NULL column to a
    // table that already holds rows unless there is a default to backfill them with, and without
    // it the ALTER silently fails and every query then blows up on the missing column.

    /** When it was issued - used to rate limit "resend". */
    @Column(nullable = false, columnDefinition = "bigint default 0")
    @Builder.Default
    private Long createdAt = System.currentTimeMillis();

    /**
     * Wrong guesses so far. Six digits is only a million combinations, so without a cap an
     * attacker could work through them all against a known email address.
     */
    @Column(nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private int attempts = 0;

    @Builder.Default
    private boolean used = false;
}
