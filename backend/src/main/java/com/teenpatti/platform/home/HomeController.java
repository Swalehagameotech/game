package com.teenpatti.platform.home;

import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.common.security.CurrentUser;
import com.teenpatti.platform.home.dto.HomeDashboardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for backend-driven home page session aggregate.
 */
@Slf4j
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<HomeDashboardResponse>> getDashboard(@CurrentUser String userId) {
        HomeDashboardResponse dashboard = homeService.getHomeDashboard(userId);
        return ResponseEntity.ok(ApiResponse.success("Home dashboard payload retrieved successfully", dashboard));
    }
}
