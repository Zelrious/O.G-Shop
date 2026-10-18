package com.oldbutgold.shop.modules.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    @org.springframework.data.jpa.repository.Query("select distinct u.id from UserEntity u join u.roles r where u.status='ACTIVE' and r.roleName='KTV'")
    java.util.List<Long> findActiveKtvIds();
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserEntity u where u.id=:id")
    Optional<UserEntity> findLockedById(@org.springframework.data.repository.query.Param("id") long id);
}
