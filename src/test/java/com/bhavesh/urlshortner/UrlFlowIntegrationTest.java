package com.bhavesh.urlshortner;

import com.bhavesh.urlshortner.model.Url;
import com.bhavesh.urlshortner.repository.UrlRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UrlFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    UrlRepository urlRepository;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void createThenRedirect() throws Exception {
        String alias = uniqueAlias("flow");

        createLink(linkJson(alias), uniqueIp())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value(alias))
                .andExpect(jsonPath("$.shortUrl").value(endsWith("/" + alias)));

        mockMvc.perform(get("/" + alias))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/page"));
    }

    @Test
    void generatedCodeWorksWithoutAnAlias() throws Exception {
        MvcResult result = createLink("{\"longUrl\":\"https://example.com/generated\"}", uniqueIp())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value(matchesPattern("[0-9a-zA-Z]+")))
                .andReturn();

        String code = JsonPath.read(result.getResponse().getContentAsString(), "$.shortCode");

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/generated"));
    }

    @Test
    void duplicateAliasReturnsConflict() throws Exception {
        String alias = uniqueAlias("dup");
        String ip = uniqueIp();

        createLink(linkJson(alias), ip).andExpect(status().isCreated());
        createLink(linkJson(alias), ip)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already in use")));
    }

    @Test
    void invalidUrlReturns400InOurErrorFormat() throws Exception {
        createLink("{\"longUrl\":\"ftp://example.com\"}", uniqueIp())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("http or https")))
                .andExpect(jsonPath("$.path").value("/api/v1/urls"));
    }

    @Test
    void blankUrlReturns400NamingTheField() throws Exception {
        createLink("{\"longUrl\":\"\"}", uniqueIp())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("longUrl")));
    }

    @Test
    void malformedJsonReturns400WithAMessage() throws Exception {
        createLink("{bad", uniqueIp())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void unknownCodeReturns404Json() throws Exception {
        mockMvc.perform(get("/" + uniqueAlias("nope")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("No URL found")));
    }

    @Test
    void expiredLinkReturnsGone() throws Exception {
        String alias = uniqueAlias("exp");
        Url url = new Url("https://example.com/old", Instant.now().minusSeconds(60));
        url.setShortCode(alias);
        urlRepository.save(url);

        mockMvc.perform(get("/" + alias))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value(containsString("expired")));
    }

    @Test
    void firstRedirectPopulatesTheCache() throws Exception {
        String alias = uniqueAlias("cache");
        createLink(linkJson(alias), uniqueIp()).andExpect(status().isCreated());
        assertThat(redis.hasKey("url:" + alias)).isFalse();

        mockMvc.perform(get("/" + alias)).andExpect(status().isFound());

        assertThat(redis.hasKey("url:" + alias)).isTrue();
        assertThat(redis.getExpire("url:" + alias)).isBetween(1L, 3600L);
    }

    @Test
    void creatingALinkClearsAStaleNotFoundEntry() throws Exception {
        String alias = uniqueAlias("late");

        mockMvc.perform(get("/" + alias)).andExpect(status().isNotFound());
        assertThat(redis.opsForValue().get("url:" + alias)).isEqualTo("-");

        createLink(linkJson(alias), uniqueIp()).andExpect(status().isCreated());

        mockMvc.perform(get("/" + alias)).andExpect(status().isFound());
    }

    @Test
    void clicksAreRecordedAsynchronouslyAndShowUpInStats() throws Exception {
        String alias = uniqueAlias("click");
        createLink(linkJson(alias), uniqueIp()).andExpect(status().isCreated());

        mockMvc.perform(get("/" + alias)
                        .header("Referer", "https://www.google.com/search?q=x")
                        .header("User-Agent", "integration-test"))
                .andExpect(status().isFound());

        // the click is written by a background thread, so poll instead of asserting once
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/urls/" + alias + "/stats"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.clickCount").value(1))
                        .andExpect(jsonPath("$.topReferrers[0].referrer").value("www.google.com")));
    }
}