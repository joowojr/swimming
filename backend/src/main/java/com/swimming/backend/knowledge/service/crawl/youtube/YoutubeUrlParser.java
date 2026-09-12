package com.swimming.backend.knowledge.service.crawl.youtube;

import com.swimming.backend.common.util.UrlUtils;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 유튜브 링크에서 영상 ID를 뽑고, 중복 판정 기준이 될 정본 주소를 만든다.
 *
 * <p>같은 영상이 여러 모양으로 들어온다. 공유 버튼은 {@code youtu.be}를, 앱은 {@code m.}을,
 * 쇼츠는 {@code /shorts/}를 쓰고, 재생 위치({@code t})나 재생목록({@code list})이 붙기도 한다.
 * 이것들을 하나의 {@code watch?v=} 주소로 모아야 같은 영상을 두 번 저장하지 않는다.
 *
 * <p>주소를 다루는 일반적인 처리는 {@link UrlUtils}에 있다. 여기 남긴 것은 유튜브에만
 * 해당하는 지식뿐이다. 세션의 음악 URL 검증도 유튜브 주소를 보지만 규칙이 다르다. 그쪽은
 * 임베드할 수 있는지를 묻기 때문에 재생목록을 허용하고 ID 형식을 따지지 않는다.
 */
public final class YoutubeUrlParser {

    private static final String WATCH_DOMAIN = "youtube.com";

    private static final String SHORT_DOMAIN = "youtu.be";

    /** 경로 한 칸 뒤에 영상 ID가 오는 형태들. */
    private static final Set<String> ID_IN_PATH = Set.of("shorts", "live", "embed", "v");

    /** 유튜브 영상 ID는 11자의 URL-safe base64다. */
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    private YoutubeUrlParser() {
    }

    public static boolean isYoutubeHost(URI uri) {
        return uri != null
                && (UrlUtils.hasHostOrSubdomain(uri, WATCH_DOMAIN)
                || UrlUtils.hasHostOrSubdomain(uri, SHORT_DOMAIN));
    }

    /**
     * @return 영상 ID. 유튜브 링크가 아니거나 영상을 가리키지 않으면 비어 있다.
     */
    public static Optional<String> videoIdOf(URI uri) {
        if (uri == null) {
            return Optional.empty();
        }

        List<String> segments = UrlUtils.pathSegments(uri);

        if (UrlUtils.hasHostOrSubdomain(uri, SHORT_DOMAIN)) {
            return validate(segments.isEmpty() ? null : segments.getFirst());
        }
        if (!UrlUtils.hasHostOrSubdomain(uri, WATCH_DOMAIN)) {
            return Optional.empty();
        }

        if (segments.size() >= 2 && ID_IN_PATH.contains(lowercase(segments.getFirst()))) {
            return validate(segments.get(1));
        }
        if (segments.size() == 1 && "watch".equals(lowercase(segments.getFirst()))) {
            return validate(UrlUtils.queryParameter(uri, "v").orElse(null));
        }

        return Optional.empty();
    }

    /**
     * 재생 위치나 재생목록을 떼어낸 정본 주소.
     *
     * <p>수집기가 문서의 canonical URL로 쓴다. 같은 영상이 어떤 모양으로 들어와도 같은
     * 문자열이 나와야 중복 저장을 막을 수 있다.
     */
    public static String watchUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    private static String lowercase(String segment) {
        return segment.toLowerCase(Locale.ROOT);
    }

    private static Optional<String> validate(String candidate) {
        if (candidate == null || !VIDEO_ID.matcher(candidate).matches()) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }
}
