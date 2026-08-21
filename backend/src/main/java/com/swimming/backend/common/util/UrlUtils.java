package com.swimming.backend.common.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

public final class UrlUtils {

    private UrlUtils() {
    }

    public static Optional<URI> parseHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return Optional.empty();
            }
            return Optional.of(uri);
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
    }

    public static boolean hasHostOrSubdomain(URI uri, String domain) {
        String host = uri.getHost();
        if (host == null || domain == null || domain.isBlank()) {
            return false;
        }

        String normalizedHost = host.toLowerCase();
        String normalizedDomain = domain.toLowerCase();
        return normalizedHost.equals(normalizedDomain)
                || normalizedHost.endsWith("." + normalizedDomain);
    }

    public static boolean hasNonEmptyQueryParameter(URI uri, String parameterName) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || parameterName == null || parameterName.isBlank()) {
            return false;
        }

        for (String parameter : rawQuery.split("&")) {
            int separator = parameter.indexOf('=');
            if (separator > 0
                    && parameter.substring(0, separator).equals(parameterName)
                    && separator < parameter.length() - 1) {
                return true;
            }
        }
        return false;
    }
}
