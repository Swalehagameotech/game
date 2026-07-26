package com.teenpatti.platform.home;

import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.home.dto.HomeDashboardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing REST API for backend-driven Home Page Dashboard aggregation.
 */
@Slf4j
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<HomeDashboardResponse>> getDashboard(Authentication authentication) {
        String userId = authentication != null && authentication.getPrincipal() != null ? authentication.getPrincipal().toString() : "guest";
        HomeDashboardResponse dashboard = homeService.getHomeDashboard(userId);
        return ResponseEntity.ok(ApiResponse.success("Home dashboard payload retrieved successfully", dashboard));
    }
}
