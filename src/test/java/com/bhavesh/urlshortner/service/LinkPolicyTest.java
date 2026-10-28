package com.bhavesh.urlshortner.service;

import com.bhavesh.urlshortner.exception.InvalidAliasException;
import com.bhavesh.urlshortner.exception.InvalidUrlException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LinkPolicyTest {

    private final LinkPolicy policy = new LinkPolicy("https://sho.rt");

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/path?x=1#frag",
            "https://sub.example.co.in/a%20b"
    })
    void acceptsNormalUrls(String url) {
        assertDoesNotThrow(() -> policy.validateLongUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com", "javascript:alert(1)", "file:///etc/passwd",
            "example.com", "//example.com",
            "http://localhost", "http://localhost:8080/x",
            "http://127.0.0.1", "http://2130706433", "http://0x7f.1", "http://[::1]/",
            "http://10.0.0.5/admin", "http://intranet/", "http://printer.local/",
            "http://user:pass@example.com", "https://sho.rt/abc"
    })
    void rejectsUnsafeUrls(String url) {
        assertThrows(InvalidUrlException.class, () -> policy.validateLongUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"my-link", "summer_sale", "a-b", "Launch-2026"})
    void acceptsValidAliases(String alias) {
        assertDoesNotThrow(() -> policy.validateAlias(alias));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "abc", "has space", "dot.dot", "with/slash", "swagger-ui", "admin"})
    void rejectsInvalidAliases(String alias) {
        assertThrows(InvalidAliasException.class, () -> policy.validateAlias(alias));
    }
}
