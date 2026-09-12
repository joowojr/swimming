package com.swimming.backend.common.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
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
        return queryParameter(uri, parameterName).isPresent();
    }

    /** 값이 비어 있는 파라미터는 없는 것으로 본다. {@code ?v=} 는 대상을 가리키지 않는다. */
    public static Optional<String> queryParameter(URI uri, String parameterName) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || parameterName == null || parameterName.isBlank()) {
            return Optional.empty();
        }

        for (String parameter : rawQuery.split("&")) {
            int separator = parameter.indexOf('=');
            if (separator > 0
                    && parameter.substring(0, separator).equals(parameterName)
                    && separator < parameter.length() - 1) {
                return Optional.of(parameter.substring(separator + 1));
            }
        }
        return Optional.empty();
    }

    /** 빈 칸을 걸러낸 경로 조각. {@code /shorts/abc} 는 {@code [shorts, abc]} 가 된다. */
    public static List<String> pathSegments(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return List.of();
        }

        return Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toList();
    }
}
