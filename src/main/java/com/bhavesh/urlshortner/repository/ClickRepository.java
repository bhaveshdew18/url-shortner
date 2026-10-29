package com.bhavesh.urlshortner.repository;

import com.bhavesh.urlshortner.model.Click;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ClickRepository extends JpaRepository<Click, Long> {

    long countByUrlIdAndClickedAtAfter(Long urlId, Instant since);

    @Query(value = """
            SELECT referrer_host AS referrer, COUNT(*) AS clicks
            FROM clicks
            WHERE url_id = :urlId AND referrer_host IS NOT NULL
            GROUP BY referrer_host
            ORDER BY clicks DESC
            LIMIT 5
            """, nativeQuery = true)
    List<ReferrerStat> topReferrers(@Param("urlId") Long urlId);
}
