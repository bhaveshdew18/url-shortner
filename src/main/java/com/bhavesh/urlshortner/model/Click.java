package com.bhavesh.urlshortner.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "clicks")
public class Click {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url_id", nullable = false, updatable = false)
    private Long urlId;

    @Column(name = "clicked_at", nullable = false, updatable = false)
    private Instant clickedAt;

    @Column(name = "referrer_host", length = 255)
    private String referrerHost;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    protected Click() {}

    public Click(Long urlId, Instant clickedAt, String referrerHost, String userAgent) {
        this.urlId = urlId;
        this.clickedAt = clickedAt;
        this.referrerHost = referrerHost;
        this.userAgent = userAgent;
    }
}
