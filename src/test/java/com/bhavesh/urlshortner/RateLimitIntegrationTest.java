package com.bhavesh.urlshortner;

import com.bhavesh.urlshortner.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitIntegrationTest extends AbstractIntegrationTest {

    private static final String BODY = "{\"longUrl\":\"https://example.com/limited\"}";

    @Autowired
    RateLimitProperties props;

    private void exhaust(String ip) throws Exception {
        for (long i = 0; i < props.capacity(); i++) {
            createLink(BODY, ip).andExpect(status().isCreated());
        }
    }

    @Test
    void createBeyondTheLimitGets429WithRetryAfter() throws Exception {
        String ip = uniqueIp();
        exhaust(ip);

        createLink(BODY, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value(containsString("Too many requests")));
    }

    @Test
    void limitIsTrackedPerClient() throws Exception {
        String busy = uniqueIp();
        exhaust(busy);

        createLink(BODY, busy).andExpect(status().isTooManyRequests());
        createLink(BODY, uniqueIp()).andExpect(status().isCreated());
    }

    @Test
    void redirectsAreNotRateLimited() throws Exception {
        String alias = uniqueAlias("free");
        String ip = uniqueIp();
        createLink(linkJson(alias), ip).andExpect(status().isCreated());
        // linkJson used one token; use up the rest
        for (long i = 1; i < props.capacity(); i++) {
            createLink(BODY, ip).andExpect(status().isCreated());
        }
        createLink(BODY, ip).andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/" + alias).with(fromIp(ip))).andExpect(status().isFound());
    }

    @Test
    void spoofedForwardedForHeaderDoesNotBypassTheLimit() throws Exception {
        String ip = uniqueIp();
        for (long i = 0; i < props.capacity(); i++) {
            mockMvc.perform(post("/api/v1/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY)
                            .header("X-Forwarded-For", "203.0.113." + (i % 250))
                            .with(fromIp(ip)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .header("X-Forwarded-For", "198.51.100.99")
                        .with(fromIp(ip)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void rateLimitHeadersAreReturned() throws Exception {
        createLink(BODY, uniqueIp())
                .andExpect(status().isCreated())
                .andExpect(header().string("X-RateLimit-Limit", String.valueOf(props.capacity())))
                .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(props.capacity() - 1)));
    }
}