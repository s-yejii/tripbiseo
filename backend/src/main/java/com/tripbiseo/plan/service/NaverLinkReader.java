package com.tripbiseo.plan.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 네이버 블로그 글·네이버 지도 장소 링크 → 글 또는 장소 이름. 공개 페이지를 한 번만 GET 해서 og 메타만 읽어요.
 * 본문 긁기·여러 번 요청·로그인은 하지 않아요. 막히면 null (부르는 쪽이 붙여 넣은 글을 씀).
 * 안전: 주소 형식을 검사해 뽑은 값으로 네이버 주소를 새로 만들어요. naver.me 짧은 링크도 도착지가 같은 형식일 때만 써요.
 */
@Component
public class NaverLinkReader {

    private static final Pattern BLOG_URL = Pattern.compile(
            "^https?://(?:m\\.)?blog\\.naver\\.com/([A-Za-z0-9_-]+)/(\\d+)(?:[?#/]\\S*)?$");
    private static final Pattern BLOG_VIEW_URL = Pattern.compile(
            "^https?://(?:m\\.)?blog\\.naver\\.com/PostView\\.n\\w+\\?\\S*blogId=([A-Za-z0-9_-]+)\\S*logNo=(\\d+)\\S*$");
    private static final Pattern PLACE_URL = Pattern.compile(
            "^https?://(?:map\\.naver\\.com/\\S*?/place/|(?:m\\.|pcmap\\.)?place\\.naver\\.com/[a-z]+/)(\\d+)(?:[?#/]\\S*)?$");
    private static final Pattern SHORT_URL = Pattern.compile("^https?://naver\\.me/([A-Za-z0-9]+)/?$");
    private static final Pattern TITLE_TAIL = Pattern.compile("\\s*[:|-]\\s*네이버.*$"); // "온점 : 네이버" → "온점"

    private final InstagramMetaReader pageReader;

    public NaverLinkReader(InstagramMetaReader pageReader) {
        this.pageReader = pageReader;
    }

    /** 네이버 블로그 글이면 읽을 모바일 주소. 아니면 null */
    public String toBlogUrl(String url) {
        Matcher matcher = matchFirst(url, BLOG_URL, BLOG_VIEW_URL);
        return matcher == null ? null : "https://m.blog.naver.com/" + matcher.group(1) + "/" + matcher.group(2);
    }

    /** 네이버 지도 장소 번호 (우리 DB의 장소 id는 "naver-번호"). 아니면 null */
    public String toPlaceNumber(String url) {
        Matcher matcher = matchFirst(url, PLACE_URL);
        return matcher == null ? null : matcher.group(1);
    }

    /** 짧은 링크면 도착 주소(한 번만 물어봄), 아니면 그대로. 못 읽으면 null */
    public String expand(String url) {
        Matcher matcher = matchFirst(url, SHORT_URL);
        return matcher == null ? url : pageReader.fetchRedirect("https://naver.me/" + matcher.group(1));
    }

    /** 짧은 링크인지 */
    public boolean isShortUrl(String url) {
        return matchFirst(url, SHORT_URL) != null;
    }

    /** 블로그 글의 제목 + 설명. 못 읽으면 null */
    public String readBlog(String blogUrl) {
        String html = pageReader.fetchHead(blogUrl);
        if (html == null) {
            return null;
        }
        String title = pageReader.findMetaContent(html, "og:title");
        String description = pageReader.findMetaContent(html, "og:description");
        if (title == null && description == null) {
            return null;
        }
        return (title == null ? "" : title) + "\n" + (description == null ? "" : description);
    }

    /** 장소 페이지의 이름. 못 읽으면(네이버가 자주 막음) null */
    public String readPlaceName(String placeNumber) {
        String html = pageReader.fetchHead("https://m.place.naver.com/place/" + placeNumber + "/home");
        String title = html == null ? null : pageReader.findMetaContent(html, "og:title");
        if (title == null) {
            return null;
        }
        String name = TITLE_TAIL.matcher(title).replaceAll("").trim();
        return name.isEmpty() || name.equals("네이버 지도") ? null : name;
    }

    private Matcher matchFirst(String url, Pattern... patterns) {
        if (url == null) {
            return null;
        }
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(url.trim());
            if (matcher.matches()) {
                return matcher;
            }
        }
        return null;
    }
}
