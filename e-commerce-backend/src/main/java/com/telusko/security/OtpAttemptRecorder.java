package com.telusko.security;

import com.telusko.model.PasswordResetToken;
import com.telusko.model.VerificationToken;
import com.telusko.repository.PasswordResetTokenRepository;
import com.telusko.repository.VerificationTokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Records a wrong OTP guess in its own transaction.
 * <p>
 * This cannot live inside the verify method. That method is transactional and rejects a bad code
 * by throwing, which rolls the transaction back - taking the incremented counter with it. The
 * result was a cap that never applied: every guess reset to zero, leaving the six digit code open
 * to being tried exhaustively.
 * <p>
 * {@code REQUIRES_NEW} commits the increment immediately, so it survives the caller's rollback.
 * It is a separate bean because Spring cannot apply a new transaction to a self-invoked method.
 */
@Component
@RequiredArgsConstructor
public class OtpAttemptRecorder {

    private final VerificationTokenRepository vtokens;
    private final PasswordResetTokenRepository prtokens;

    /** @return the attempt count after recording this failure */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int recordVerificationFailure(Long tokenId) {
        VerificationToken vt = vtokens.findById(tokenId).orElse(null);
        if (vt == null) return 0;
        vt.setAttempts(vt.getAttempts() + 1);
        vtokens.save(vt);
        return vt.getAttempts();
    }

    /** @return the attempt count after recording this failure */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int recordResetFailure(Long tokenId) {
        PasswordResetToken pr = prtokens.findById(tokenId).orElse(null);
        if (pr == null) return 0;
        pr.setAttempts(pr.getAttempts() + 1);
        prtokens.save(pr);
        return pr.getAttempts();
    }
}
