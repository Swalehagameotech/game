package com.teenpatti.platform.admin.betting;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface BettingConfigurationRepository extends MongoRepository<BettingConfiguration, String> {
    Optional<BettingConfiguration> findByActiveTrue();
}
