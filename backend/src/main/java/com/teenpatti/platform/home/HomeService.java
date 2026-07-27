package com.teenpatti.platform.home;

import com.teenpatti.platform.home.dto.HomeDashboardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Home page facade delegating to {@link SessionAggregateService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {

    private final SessionAggregateService sessionAggregateService;

    public HomeDashboardResponse getHomeDashboard(String userId) {
        log.info("Generating home dashboard for user [{}]", userId);
        return sessionAggregateService.buildSessionAggregate(userId);
    }
}
