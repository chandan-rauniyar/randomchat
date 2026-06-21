package com.chandan.randomchat.repository;

import com.chandan.randomchat.model.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppConfigRepository extends JpaRepository<AppConfig, UUID> {

    /** Core lookup — WalletService calls this to get pricing. */
    Optional<AppConfig> findByAppIdAndConfigKey(String appId, String configKey);

    /** Load all config for an app — used at startup or admin page. */
    List<AppConfig> findByAppId(String appId);
}