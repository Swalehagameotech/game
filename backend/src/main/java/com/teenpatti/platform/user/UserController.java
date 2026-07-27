package com.teenpatti.platform.user;

import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.common.security.CurrentUser;
import com.teenpatti.platform.user.dto.ChangePasswordRequest;
import com.teenpatti.platform.user.dto.OnlinePlayersResponse;
import com.teenpatti.platform.user.dto.PublicProfileResponse;
import com.teenpatti.platform.user.dto.UpdateProfileRequest;
import com.teenpatti.platform.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User profile and presence endpoints. KYC submission is intentionally omitted until a later module.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(@CurrentUser String userId) {
        UserProfileResponse profile = userService.getUserProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved successfully", profile));
    }

    @GetMapping("/online/count")
    public ResponseEntity<ApiResponse<OnlinePlayersResponse>> getOnlineCount() {
        OnlinePlayersResponse response = userService.getOnlinePlayersCount();
        return ResponseEntity.ok(ApiResponse.success("Online player count retrieved", response));
    }

    @GetMapping("/{userId}/public")
    public ResponseEntity<ApiResponse<PublicProfileResponse>> getPublicProfile(@PathVariable String userId) {
        PublicProfileResponse publicProfile = userService.getPublicProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("Public profile retrieved successfully", publicProfile));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @CurrentUser String userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserProfileResponse updatedProfile = userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", updatedProfile));
    }

    @PostMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @CurrentUser String userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    @PostMapping("/me/presence/heartbeat")
    public ResponseEntity<ApiResponse<UserProfileResponse>> heartbeat(@CurrentUser String userId) {
        UserProfileResponse profile = userService.recordHeartbeat(userId);
        return ResponseEntity.ok(ApiResponse.success("Presence updated", profile));
    }

    @PostMapping("/me/presence/offline")
    public ResponseEntity<ApiResponse<Void>> goOffline(@CurrentUser String userId) {
        userService.markOffline(userId);
        return ResponseEntity.ok(ApiResponse.success("Marked offline", null));
    }

    @PostMapping("/tutorial/complete")
    public ResponseEntity<ApiResponse<Void>> completeTutorial(@CurrentUser String userId) {
        userService.completeTutorial(userId);
        return ResponseEntity.ok(ApiResponse.success("Tutorial marked as completed", null));
    }
}
