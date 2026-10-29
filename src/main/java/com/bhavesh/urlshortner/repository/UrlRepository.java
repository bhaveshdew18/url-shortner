package com.bhavesh.urlshortner.repository;

import com.bhavesh.urlshortner.model.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UrlRepository extends JpaRepository<Url, Long> {
    Optional<Url> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    @Modifying
    @Query("update Url u set u.clickCount = u.clickCount + 1 where u.id = :id")
    int incrementClickCount(@Param("id") Long id);
}
