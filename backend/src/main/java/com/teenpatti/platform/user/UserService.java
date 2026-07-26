package com.teenpatti.platform.user;

import com.teenpatti.platform.common.exception.DuplicateUserException;
import com.teenpatti.platform.common.exception.UserNotFoundException;
import com.teenpatti.platform.user.dto.PublicProfileResponse;
import com.teenpatti.platform.user.dto.UpdateProfileRequest;
import com.teenpatti.platform.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service encapsulating User module operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserProfileResponse getUserProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return UserMapper.toUserProfileResponse(user);
    }

    public PublicProfileResponse getPublicProfile(String targetUserId) {
        User user = userRepository.findById(targetUserId)
                .filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return UserMapper.toPublicProfileResponse(user);
    }

    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            String newDisplayName = request.getDisplayName().trim();
            if (!newDisplayName.equals(user.getDisplayName())) {
                if (userRepository.existsByDisplayNameAndIdNot(newDisplayName, userId)) {
                    throw new DuplicateUserException("Display name '" + newDisplayName + "' is already taken.");
                }
                user.setDisplayName(newDisplayName);
            }
        }

        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }

        User savedUser = userRepository.save(user);
        log.info("Updated profile for user [{}]", userId);
        return UserMapper.toUserProfileResponse(savedUser);
    }
}
