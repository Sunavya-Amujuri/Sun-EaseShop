package com.telusko.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * A one-time code emailed to authorise a password reset.
 * The same rules as {@link VerificationToken}: {@link #token} is a BCrypt hash of the emailed
 * code, never the code itself.
 */
@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PasswordResetToken {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** BCrypt hash of the emailed code. */
    @Column(nullable = false, length = 120)
    private String token;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    @Column(nullable = false)
    private Long expiresAt;

    // Database defaults so ddl-auto=update can add these columns to a table that already has
    // rows - see the note in VerificationToken.

    /** When it was issued - used to rate limit "resend". */
    @Column(nullable = false, columnDefinition = "bigint default 0")
    @Builder.Default
    private Long createdAt = System.currentTimeMillis();

    /** Wrong guesses so far; the code dies once this hits the cap. */
    @Column(nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private int attempts = 0;

    @Builder.Default
    private boolean used = false;
}
