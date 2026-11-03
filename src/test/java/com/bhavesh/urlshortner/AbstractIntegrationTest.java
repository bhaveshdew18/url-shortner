package com.bhavesh.urlshortner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfig.class)
public abstract class AbstractIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    protected MockMvc mockMvc;

    /** Every test gets its own client IP, so rate-limit buckets never interfere. */
    protected static String uniqueIp() {
        int n = COUNTER.incrementAndGet();
        return "10.20." + (n / 250) + "." + (n % 250 + 1);
    }

    /** Aliases must be unique because all tests share one database and Redis. */
    protected static String uniqueAlias(String prefix) {
        return prefix + "-" + Long.toString(Math.abs(System.nanoTime()), 36);
    }

    protected static String linkJson(String alias) {
        return "{\"longUrl\":\"https://example.com/page\",\"customAlias\":\"" + alias + "\"}";
    }

    protected static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    protected ResultActions createLink(String json, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .with(fromIp(ip)));
    }
}