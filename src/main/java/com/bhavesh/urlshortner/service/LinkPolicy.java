package com.bhavesh.urlshortner.service;

import com.bhavesh.urlshortner.exception.InvalidAliasException;
import com.bhavesh.urlshortner.exception.InvalidUrlException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class LinkPolicy {

    private static final int MAX_URL_LENGTH = 2048;
    private static final Pattern ALIAS_CHARS = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");
    private static final Pattern NUMERIC_HOST = Pattern.compile(
            "^(0x[0-9a-f]+|\\d+)(\\.(0x[0-9a-f]+|\\d+)){0,3}$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> RESERVED_ALIASES =
            Set.of("api", "actuator", "swagger-ui", "v3", "health", "admin", "error");
    private static final List<String> BLOCKED_SUFFIXES =
            List.of(".localhost", ".local", ".internal", ".home.arpa");

    private static final Pattern CODE_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    public boolean isValidCodeFormat(String code) {
        return code != null && CODE_FORMAT.matcher(code).matches();
    }

    private final String appHost;

    public  LinkPolicy(@Value("${app.base-url}") String baseUrl) {
        this.appHost = URI.create(baseUrl).getHost();
    }

    public void validateLongUrl(String raw) {
        if (raw.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("URL is too long (max " + MAX_URL_LENGTH + "characters)");
        }
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("URL is not well-formed");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidUrlException("Only http or https URLs are allowed");
        }
        if (uri.getRawUserInfo() != null) {
            throw new InvalidUrlException("URLs containing credentials are not allowed");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new InvalidUrlException("URL must contain a valid host");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);

        if (host.startsWith("[") || NUMERIC_HOST.matcher(host).matches()) {
            throw new InvalidUrlException("IP addresses are not allowed; use a domain name");
        }
        if (!host.contains(".")) {
            throw new InvalidUrlException("Host must be a fully qualified domain name");
        }
        if (BLOCKED_SUFFIXES.stream().anyMatch(host::endsWith)) {
            throw new InvalidUrlException("Internal hostnames are not allowed");
        }
        if (host.equals(appHost)) {
            throw new InvalidUrlException("Links to this service itself are not allowed");
        }
    }

    public void validateAlias(String alias) {
        if (!ALIAS_CHARS.matcher(alias).matches()) {
            throw new InvalidAliasException(
                    "Alias must be 3-32 characters: letters, digits, '-' or '_'");
        }
        if (alias.indexOf('-') < 0 && alias.indexOf('_') < 0) {
            throw new InvalidAliasException("Alias must contain at least one '-' or '_'");
        }
        if (RESERVED_ALIASES.contains(alias.toLowerCase(Locale.ROOT))) {
            throw new InvalidAliasException("This alias is reserved");
        }
    }
}

