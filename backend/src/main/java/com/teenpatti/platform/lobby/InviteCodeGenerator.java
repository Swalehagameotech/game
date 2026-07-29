package com.teenpatti.platform.lobby;

import com.teenpatti.platform.table.TableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Utility component for generating unique, non-predictable random alphanumeric invite codes for private tables.
 * Enforces database uniqueness checks and retries on collision.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InviteCodeGenerator {

    private static final String ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Excludes ambiguous 'I', 'O', '0', '1'
    // UI and product copy expect a 6-character invite code.
    private static final int CODE_LENGTH = 6;
    private static final int MAX_COLLISION_RETRIES = 10;
    private final SecureRandom random = new SecureRandom();

    private final TableRepository tableRepository;

    public String generateUniqueInviteCode() {
        int attempts = 0;
        while (attempts < MAX_COLLISION_RETRIES) {
            attempts++;
            String candidateCode = generateRandomCode();
            if (tableRepository.findByInviteCode(candidateCode).isEmpty()) {
                return candidateCode;
            }
            log.warn("Invite code collision detected for candidate [{}]. Retrying ({}/{})",
                    candidateCode, attempts, MAX_COLLISION_RETRIES);
        }
        throw new IllegalStateException("Failed to generate a unique invite code after " + MAX_COLLISION_RETRIES + " attempts.");
    }

    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = random.nextInt(ALPHANUMERIC.length());
            sb.append(ALPHANUMERIC.charAt(index));
        }
        return sb.toString();
    }
}
