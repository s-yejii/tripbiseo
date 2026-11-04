package com.tripbiseo.plan.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * 인스타 공개 게시물 페이지를 한 번만 GET 해서 og:description(없으면 og:title)의 게시물 글을 읽어요.
 * 로그인·크롤링은 하지 않고, 막히면 null을 돌려줘요 (부르는 쪽이 사용자가 붙여 넣은 글을 씀).
 * 안전: 사용자가 준 주소를 그대로 부르지 않고, 형식을 검사해 뽑은 게시물 코드로 주소를 새로 만들어요.
 *       리다이렉트도 따라가지 않아요. 그래서 이 기능으로 인스타 말고 다른 서버를 부를 수 없어요.
 */
@Component
public class InstagramMetaReader {

    private static final Logger log = LoggerFactory.getLogger(InstagramMetaReader.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_PAGE_BYTES = 512 * 1024; // 앞부분(head)만 있으면 되니까 이만큼만 읽음
    private static final int HTTP_OK = 200;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; tripbiseo/1.0)";

    // instagram.com, www, m 만 허용. 주소 모양: /p/코드 · /reel/코드 · /reels/코드 · /tv/코드 (앞에 /계정이름/ 이 붙기도 함)
    private static final Pattern POST_URL = Pattern.compile(
            "^https?://(?:www\\.|m\\.)?instagram\\.com/(?:[A-Za-z0-9._]+/)?(p|reel|reels|tv)/([A-Za-z0-9_-]+)/?(?:[?#]\\S*)?$");
    private static final Pattern META_TAG = Pattern.compile("<meta\\s[^>]*>");
    private static final Pattern CONTENT = Pattern.compile("content=\"([^\"]*)\"");
    // og 값 모양: 좋아요 수·계정 이름·날짜 + : "게시물 글". 따옴표 안의 글만 꺼내서 계정 이름은 버림. (?s): 글이 여러 줄이어도 끝까지
    private static final Pattern QUOTED_CAPTION = Pattern.compile("(?s):\\s*[\"“](.*)[\"”]");

    // HttpClient: 자바에 들어 있는 HTTP 도구. 기본값이 "리다이렉트를 따라가지 않음"이라 이걸 씀
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    /** 인스타 게시물·릴스 주소면 깨끗한 주소로 (추적 값 등 뒤쪽은 버림). 아니면 null */
    public String toPageUrl(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = POST_URL.matcher(url.trim());
        if (!matcher.matches()) {
            return null;
        }
        return "https://www.instagram.com/" + matcher.group(1) + "/" + matcher.group(2) + "/";
    }

    /** 페이지에서 게시물 글 읽기. 막혔거나(로그인 화면·오류·리다이렉트) 글이 없으면 null */
    public String readCaption(String pageUrl) {
        String html = fetchHead(pageUrl);
        if (html == null) {
            return null;
        }
        String caption = extractCaption(findMetaContent(html, "og:description"));
        return caption != null ? caption : extractCaption(findMetaContent(html, "og:title"));
    }

    // ---------------------------------------------------------------- 도우미

    /** GET 한 번. 200이 아니거나 연결이 안 되면 null */
    String fetchHead(String pageUrl) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(pageUrl))
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "ko")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != HTTP_OK) {
                    log.info("링크 페이지를 읽지 못함: 상태 {}", response.statusCode());
                    return null;
                }
                return new String(body.readNBytes(MAX_PAGE_BYTES), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.info("링크 페이지 연결 실패: {}", e.getClass().getSimpleName());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 스레드를 멈추라는 신호를 지우지 않고 남겨 둠
            return null;
        }
    }

    /** 리다이렉트 도착 주소만 물어봄 (따라가지는 않음). 없으면 null */
    String fetchRedirect(String shortUrl) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(shortUrl)).timeout(TIMEOUT).header("User-Agent", USER_AGENT).GET().build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.headers().firstValue("Location").orElse(null);
        } catch (IOException e) {
            log.info("짧은 링크 연결 실패: {}", e.getClass().getSimpleName());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** <meta property="이름" content="값">의 값 (&quot; 같은 기호는 글자로 되돌림). 없으면 null */
    String findMetaContent(String html, String property) {
        Matcher tags = META_TAG.matcher(html);
        while (tags.find()) {
            String tag = tags.group();
            if (tag.contains("property=\"" + property + "\"")) {
                Matcher content = CONTENT.matcher(tag);
                return content.find() ? HtmlUtils.htmlUnescape(content.group(1)) : null;
            }
        }
        return null;
    }

    /** 따옴표 안의 게시물 글만. 모양이 다르면(로그인 화면 제목 등) null */
    private String extractCaption(String metaValue) {
        if (metaValue == null) {
            return null;
        }
        Matcher matcher = QUOTED_CAPTION.matcher(metaValue);
        if (!matcher.find() || matcher.group(1).isBlank()) {
            return null;
        }
        return matcher.group(1).trim();
    }
}
