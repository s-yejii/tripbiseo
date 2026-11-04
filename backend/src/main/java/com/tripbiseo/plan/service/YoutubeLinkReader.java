package com.tripbiseo.plan.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 유튜브 영상 링크 → 영상 글. 공식 방법만 써요 (페이지 HTML을 긁지 않고, 다른 사람 영상의 자막도 안 받아요).
 *   키가 있으면(YOUTUBE_API_KEY) YouTube Data API로 제목 + 설명, 없으면 oEmbed로 제목만.
 * 안전: 사용자가 준 주소를 그대로 부르지 않고, 영상 번호만 뽑아 주소를 새로 만들어요.
 */
@Component
public class YoutubeLinkReader {

    private static final String API_URL = "https://www.googleapis.com/youtube/v3/videos?part=snippet&id=";
    private static final String OEMBED_URL = "https://www.youtube.com/oembed?format=json&url=";
    // watch?v=번호 · youtu.be/번호 · shorts/번호 · live/번호 (www, m 허용)
    private static final Pattern VIDEO_URL = Pattern.compile(
            "^https?://(?:(?:www\\.|m\\.)?youtube\\.com/(?:watch\\?(?:\\S*&)?v=|shorts/|live/)|youtu\\.be/)([A-Za-z0-9_-]{6,20})(?:[?&#/]\\S*)?$");

    private final InstagramMetaReader pageReader;
    private final ObjectMapper objectMapper;

    @Value("${YOUTUBE_API_KEY:}")
    private String apiKey;

    public YoutubeLinkReader(InstagramMetaReader pageReader, ObjectMapper objectMapper) {
        this.pageReader = pageReader;
        this.objectMapper = objectMapper;
    }

    /** 유튜브 영상 주소면 영상 번호. 아니면 null */
    public String toVideoId(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = VIDEO_URL.matcher(url.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    /** 영상 글 (제목 + 설명, 키가 없으면 제목만). 못 읽으면 null */
    public String readText(String videoId) {
        String text = apiKey.isBlank() ? null : readFromApi(videoId);
        return text != null ? text : readFromOembed(videoId);
    }

    // ---------------------------------------------------------------- 도우미

    private String readFromApi(String videoId) {
        JsonNode root = fetchJson(API_URL + videoId + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        if (root == null || !root.path("items").has(0)) {
            return null;
        }
        JsonNode snippet = root.path("items").get(0).path("snippet");
        return snippet.path("title").asString("") + "\n" + snippet.path("description").asString("");
    }

    private String readFromOembed(String videoId) {
        String watchUrl = "https://www.youtube.com/watch?v=" + videoId;
        JsonNode root = fetchJson(OEMBED_URL + URLEncoder.encode(watchUrl, StandardCharsets.UTF_8));
        String title = root == null ? "" : root.path("title").asString("");
        return title.isBlank() ? null : title;
    }

    /** GET 한 번 → JSON. 실패하면 null (오류에 키가 섞이지 않게 주소는 로그에 남기지 않음) */
    private JsonNode fetchJson(String address) {
        String body = pageReader.fetchHead(address);
        if (body == null) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
