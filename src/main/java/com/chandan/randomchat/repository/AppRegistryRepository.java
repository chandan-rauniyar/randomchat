package com.chandan.randomchat.repository;

// =============================================================================
// AppRegistryRepository.java
// =============================================================================
import com.chandan.randomchat.model.AppRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AppRegistryRepository extends JpaRepository<AppRegistry, UUID> {

    /** Used by AppIdInterceptor on every request — must be fast (indexed). */
    boolean existsByAppIdAndIsActiveTrue(String appId);

    Optional<AppRegistry> findByAppId(String appId);
}
 
