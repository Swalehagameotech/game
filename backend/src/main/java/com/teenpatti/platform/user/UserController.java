package com.teenpatti.platform.user;

import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.common.security.CurrentUser;
import com.teenpatti.platform.user.dto.PublicProfileResponse;
import com.teenpatti.platform.user.dto.UpdateProfileRequest;
import com.teenpatti.platform.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller exposing User module endpoints.
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

    @PostMapping("/me/kyc")
    public ResponseEntity<ApiResponse<UserProfileResponse>> submitKyc(
            @CurrentUser String userId,
            @Valid @RequestBody com.teenpatti.platform.admin.dto.KycSubmissionRequest request,
            @org.springframework.beans.factory.annotation.Autowired com.teenpatti.platform.admin.AdminKycService adminKycService) {
        User user = adminKycService.submitKyc(userId, request);
        UserProfileResponse response = userService.getUserProfile(user.getId());
        return ResponseEntity.ok(ApiResponse.success("KYC submission received and pending review", response));
    }

    @PostMapping("/tutorial/complete")
    public ResponseEntity<ApiResponse<Void>> completeTutorial(@CurrentUser String userId) {
        userService.completeTutorial(userId);
        return ResponseEntity.ok(ApiResponse.success("Tutorial marked as completed", null));
    }
}
