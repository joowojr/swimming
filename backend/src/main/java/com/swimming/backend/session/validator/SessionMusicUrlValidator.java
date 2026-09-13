package com.swimming.backend.session.validator;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.util.UrlUtils;

import java.net.URI;
import java.util.List;
import java.util.Set;

public final class SessionMusicUrlValidator {

    private static final String YOUTUBE_DOMAIN = "youtube.com";
    private static final String YOUTUBE_SHORT_DOMAIN = "youtu.be";
    private static final String YOUTUBE_NO_COOKIE_DOMAIN = "youtube-nocookie.com";

    /** 경로 한 칸 뒤에 재생할 대상이 오는 형태들. */
    private static final Set<String> EMBEDDABLE_PATHS = Set.of("embed", "shorts");

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
        List<String> segments = UrlUtils.pathSegments(uri);

        if (UrlUtils.hasHostOrSubdomain(uri, YOUTUBE_SHORT_DOMAIN)) {
            return !segments.isEmpty();
        }

        boolean youtubeHost = UrlUtils.hasHostOrSubdomain(uri, YOUTUBE_DOMAIN);
        boolean youtubeNoCookieHost = UrlUtils.hasHostOrSubdomain(
                uri,
                YOUTUBE_NO_COOKIE_DOMAIN
        );
        if (!youtubeHost && !youtubeNoCookieHost) {
            return false;
        }

        // 임베드 전용 도메인은 이 경로만 재생할 수 있다.
        if (!segments.isEmpty() && EMBEDDABLE_PATHS.contains(segments.getFirst())) {
            return segments.size() > 1;
        }
        if (!youtubeHost) {
            return false;
        }

        return (isPath(segments, "watch") && UrlUtils.hasNonEmptyQueryParameter(uri, "v"))
                || (isPath(segments, "playlist")
                && UrlUtils.hasNonEmptyQueryParameter(uri, "list"));
    }

    private static boolean isPath(List<String> segments, String name) {
        return segments.size() == 1 && segments.getFirst().equals(name);
    }
}
