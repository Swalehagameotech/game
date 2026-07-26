package com.teenpatti.platform.admin;

import com.teenpatti.platform.admin.dto.KycSubmissionRequest;
import com.teenpatti.platform.common.exception.ResourceNotFoundException;
import com.teenpatti.platform.user.KycStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;

import com.teenpatti.platform.notification.NotificationService;
import com.teenpatti.platform.notification.NotificationType;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminKycService {

    private final UserRepository userRepository;
    private final AdminActionLogService adminActionLogService;
    private final NotificationService notificationService;

    public User submitKyc(String userId, KycSubmissionRequest request) {
        User user = getUserOrThrow(userId);
        user.setKycStatus(KycStatus.PENDING);
        log.info("User [{}] submitted KYC document [{}: {}]", userId, request.getDocumentType(), request.getDocumentNumber());
        return userRepository.save(user);
    }

    public Page<User> listPendingKyc(Pageable pageable) {
        return userRepository.findByKycStatus(KycStatus.PENDING, pageable);
    }

    public User approveKyc(String adminUserId, String targetUserId) {
        User user = getUserOrThrow(targetUserId);

        if (user.getKycStatus() != KycStatus.PENDING) {
            throw new IllegalStateException("User KYC is not in PENDING status. Current status: " + user.getKycStatus());
        }

        user.setKycStatus(KycStatus.VERIFIED);
        User updated = userRepository.save(user);

        adminActionLogService.logAction(
                adminUserId,
                AdminActionType.KYC_APPROVE,
                targetUserId,
                Map.of("targetUserId", targetUserId)
        );

        log.info("Admin [{}] APPROVED KYC for user [{}]", adminUserId, targetUserId);
        // Phase 14: Retroactive notification call
        notificationService.notify(
                targetUserId,
                NotificationType.SYSTEM_ANNOUNCEMENT,
                "Your KYC verification has been approved."
        );
        return updated;
    }

    public User rejectKyc(String adminUserId, String targetUserId, String reason) {
        User user = getUserOrThrow(targetUserId);

        if (user.getKycStatus() != KycStatus.PENDING) {
            throw new IllegalStateException("User KYC is not in PENDING status. Current status: " + user.getKycStatus());
        }

        user.setKycStatus(KycStatus.REJECTED);
        User updated = userRepository.save(user);

        adminActionLogService.logAction(
                adminUserId,
                AdminActionType.KYC_REJECT,
                targetUserId,
                Map.of("targetUserId", targetUserId, "reason", reason)
        );

        log.info("Admin [{}] REJECTED KYC for user [{}] with reason: {}", adminUserId, targetUserId, reason);
        // Phase 14: Retroactive notification call
        notificationService.notify(
                targetUserId,
                NotificationType.SYSTEM_ANNOUNCEMENT,
                "Your KYC verification was rejected. Reason: " + reason
        );
        return updated;
    }

    private User getUserOrThrow(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }
}
