package com.swimming.backend.session.validator;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.util.UrlUtils;

import java.net.URI;

public final class SessionMusicUrlValidator {

    private static final String YOUTUBE_DOMAIN = "youtube.com";
    private static final String YOUTUBE_SHORT_DOMAIN = "youtu.be";
    private static final String YOUTUBE_NO_COOKIE_DOMAIN = "youtube-nocookie.com";

    private SessionMusicUrlValidator() {
    }

    public static void validate(String musicUrl) {
        if (!isValid(musicUrl)) {
            throw new BusinessException(ErrorCode.INVALID_MUSIC_URL);
        }
    }

    private static boolean isValid(String musicUrl) {
        if (musicUrl == null) {
            return true;
        }
        if (musicUrl.isBlank()) {
            return false;
        }

        return UrlUtils.parseHttpUrl(musicUrl)
                .map(SessionMusicUrlValidator::isYouTubeVideoOrPlaylistUrl)
                .orElse(false);
    }

    private static boolean isYouTubeVideoOrPlaylistUrl(URI uri) {
        String path = uri.getPath();
        if (UrlUtils.hasHostOrSubdomain(uri, YOUTUBE_SHORT_DOMAIN)) {
            return path != null && path.length() > 1;
        }

        boolean youtubeHost = UrlUtils.hasHostOrSubdomain(uri, YOUTUBE_DOMAIN);
        boolean youtubeNoCookieHost = UrlUtils.hasHostOrSubdomain(
                uri,
                YOUTUBE_NO_COOKIE_DOMAIN
        );
        if (!youtubeHost && !youtubeNoCookieHost) {
            return false;
        }

        if (path != null && (path.startsWith("/embed/") || path.startsWith("/shorts/"))) {
            return path.length() > path.indexOf('/', 1) + 1;
        }
        if (!youtubeHost || path == null) {
            return false;
        }
        return (path.equals("/watch") && UrlUtils.hasNonEmptyQueryParameter(uri, "v"))
                || (path.equals("/playlist")
                && UrlUtils.hasNonEmptyQueryParameter(uri, "list"));
    }
}
