package com.tripbiseo.plan.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.plan.dto.AiNeedJudge;
import com.tripbiseo.plan.dto.AiNeedVerdict;
import com.tripbiseo.plan.dto.AiNeedWords;
import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.plan.dto.PlanNote;
import com.tripbiseo.plan.dto.ReviewSnippet;
import com.tripbiseo.plan.mapper.ReviewNeedMapper;
import com.tripbiseo.trip.dto.Place;

import lombok.RequiredArgsConstructor;

/**
 * ③ 칩으로 못 고르는 요청("4명이 앉을 자리", "주차되는 곳")을 후보 장소의 리뷰로 확인 (11/04).
 * ① Gemini: 요청 → 리뷰에서 찾을 단어 ② SQL: 후보 리뷰 중 그 단어가 든 리뷰만 꺼내 단어 앞뒤만 자름
 * ③ Gemini: 곳마다 ok(맞음)·bad(안 맞음)·unknown(모름) + 근거 리뷰 글 그대로
 * ④ 서버: 근거 글이 ②에서 보낸 리뷰에 정말 있는지 확인 → 없으면 버림 (Gemini가 지어낸 근거를 막으려고)
 * 결과: ok는 점수 + 리뷰 근거(일정 칸 memo), bad는 후보에서 뺌, unknown은 그대로. 확인 결과는 notes에 요청마다 한 줄
 */
@Component
@RequiredArgsConstructor
public class ReviewNeedChecker {

    private static final int MAX_NEEDS = 3;           // 요청은 3개까지만 확인 (요청마다 Gemini를 2번 불러서)
    private static final int CHECK_PLACE_COUNT = 20;  // 점수 높은 후보 20곳만 확인
    private static final int MAX_WORDS = 6;
    private static final int MIN_WORD_LENGTH = 2;     // 한 글자 단어는 아무 데나 걸려서 뺌
    private static final int MAX_WORD_LENGTH = 10;
    private static final int REVIEWS_PER_PLACE = 4;   // 곳마다 리뷰 4개까지
    private static final int AROUND_WORD = 60;        // 찾은 단어 앞뒤 60자만 Gemini에게
    private static final int MIN_QUOTE_LENGTH = 4;    // 이보다 짧은 근거는 어디에나 있어서 믿지 않음
    private static final int MAX_QUOTE_LENGTH = 40;   // 화면에 보일 근거 길이
    private static final int MAX_NAMES_IN_NOTE = 3;
    private static final double OK_BONUS = 1.0;       // 맞는 곳 점수 (태그 하나가 0.3이라 그보다 크게)
    private static final String OK = "ok";
    private static final String BAD = "bad";

    private final AiService aiService;
    private final ReviewNeedMapper reviewNeedMapper;
    private final PlanCandidateFinder planCandidateFinder;

    /** 요청마다 후보 리뷰를 확인해 점수·리뷰 근거·후보를 고침 (candidates·scores·reasons·notes를 바로 고침) */
    public void check(PlanConditions conditions, Map<String, Place> candidates, Map<String, Double> scores,
                      Map<String, List<String>> reasons, List<PlanNote> notes) {
        List<String> needs = conditions.getNeeds();
        if (needs == null || needs.isEmpty()) {
            return;
        }
        if (!aiService.isReady()) {
            notes.add(makeNote("AI 연결이 안 돼서 리뷰로 확인하지 못했어요: " + String.join(", ", needs)));
            return;
        }
        for (int i = 0; i < needs.size() && i < MAX_NEEDS; i++) {
            List<String> placeIds = pickPlaceIds(conditions, candidates, scores); // 요청마다 다시 (앞 요청에서 뺀 곳은 빼고)
            try {
                checkNeed(needs.get(i), placeIds, candidates, scores, reasons, notes);
            } catch (ApiException e) {
                notes.add(makeNote("'" + needs.get(i) + "'은(는) AI 연결이 안 돼서 리뷰로 확인하지 못했어요"));
            }
        }
    }

    /** 요청 하나: 단어 → 리뷰 꺼내기 → 판정 → 반영. 관련 리뷰가 없으면 Gemini를 다시 부르지 않음 */
    private void checkNeed(String need, List<String> placeIds, Map<String, Place> candidates, Map<String, Double> scores,
                           Map<String, List<String>> reasons, List<PlanNote> notes) {
        List<String> words = askWords(need);
        Map<String, List<String>> textsByPlace = new LinkedHashMap<>(); // 장소 id → Gemini에게 보낸 리뷰 조각들
        if (!words.isEmpty() && !placeIds.isEmpty()) {
            List<ReviewSnippet> reviews = reviewNeedMapper.selectReviewSnippetList(placeIds, toPatterns(words), REVIEWS_PER_PLACE);
            textsByPlace = cutAroundWords(reviews, words);
        }
        if (textsByPlace.isEmpty()) {
            notes.add(makeNote("'" + need + "' — 후보 장소 리뷰에 관련 이야기가 없어서 확인하지 못했어요"));
            return;
        }
        AiNeedJudge judge = aiService.askJsonQuick(makeJudgePrompt(need, textsByPlace, candidates), AiNeedJudge.class);
        List<AiNeedVerdict> verdicts = judge == null || judge.getResults() == null ? new ArrayList<>() : judge.getResults();
        applyVerdicts(need, verdicts, textsByPlace, candidates, scores, reasons, notes);
    }

    /** 확인할 후보: 점수 높은 순 20곳 (꼭 갈 곳은 사용자가 직접 말한 곳이라 빼지 않으려고 확인 안 함) */
    private List<String> pickPlaceIds(PlanConditions conditions, Map<String, Place> candidates, Map<String, Double> scores) {
        List<String> placeIds = new ArrayList<>();
        for (Place place : planCandidateFinder.sortByScore(candidates, scores).values()) {
            if (placeIds.size() == CHECK_PLACE_COUNT) {
                break;
            }
            if (place.getId() != null && !planCandidateFinder.isMustVisit(place, conditions.getMustVisit())) {
                placeIds.add(place.getId());
            }
        }
        return placeIds;
    }

    // ---------------------------------------------------------------- ① 단어

    /** Gemini: 요청 → 리뷰에 실제로 쓰일 짧은 단어. 2~10자만, LIKE 특수문자(% _)는 지움 */
    private List<String> askWords(String need) {
        String prompt = "장소 리뷰에서 아래 요청과 관련된 이야기를 글자 검색으로 찾으려고 해.\n"
                + "리뷰에 실제로 쓰일 만한 짧은 단어를 최대 " + MAX_WORDS + "개 골라. 단어는 2~10자, 맞는 이야기와 안 맞는 이야기가 둘 다 걸리게.\n"
                + "예) 4명이 앉을 자리 → 단체석, 단체, 자리가 넓, 자리가 좁 / 주차되는 곳 → 주차\n"
                + "요청: \"" + need + "\"\n"
                + "JSON만: {\"words\": [\"단어\"]}";
        AiNeedWords answer = aiService.askJsonQuick(prompt, AiNeedWords.class); // 쉬운 일이라 빨리
        List<String> words = new ArrayList<>();
        if (answer == null || answer.getWords() == null) {
            return words;
        }
        for (String word : answer.getWords()) {
            String clean = word == null ? "" : word.replaceAll("[%_\\\\]", "").trim();
            boolean isGoodLength = clean.length() >= MIN_WORD_LENGTH && clean.length() <= MAX_WORD_LENGTH;
            if (isGoodLength && !words.contains(clean) && words.size() < MAX_WORDS) {
                words.add(clean);
            }
        }
        return words;
    }

    /** 단어 → LIKE 패턴 ("단체석" → "%단체석%") */
    private List<String> toPatterns(List<String> words) {
        List<String> patterns = new ArrayList<>();
        for (String word : words) {
            patterns.add("%" + word + "%");
        }
        return patterns;
    }

    // ---------------------------------------------------------------- ② 리뷰 조각

    /** 리뷰마다 처음 걸린 단어 앞뒤 60자만 잘라 장소별로 묶음 (리뷰 전체를 보내면 길어서) */
    private Map<String, List<String>> cutAroundWords(List<ReviewSnippet> reviews, List<String> words) {
        Map<String, List<String>> textsByPlace = new LinkedHashMap<>();
        for (ReviewSnippet review : reviews) {
            String content = review.getContent().replaceAll("\\s+", " ");
            int index = findFirstWord(content, words);
            if (index < 0) {
                continue;
            }
            String text = content.substring(Math.max(0, index - AROUND_WORD), Math.min(content.length(), index + AROUND_WORD));
            textsByPlace.computeIfAbsent(review.getPlaceId(), key -> new ArrayList<>()).add(text); // 없으면 새 목록을 만들고 더함
        }
        return textsByPlace;
    }

    /** 글에서 단어가 처음 나오는 자리 (없으면 -1) */
    private int findFirstWord(String content, List<String> words) {
        int first = -1;
        for (String word : words) {
            int index = content.indexOf(word);
            if (index >= 0 && (first < 0 || index < first)) {
                first = index;
            }
        }
        return first;
    }

    // ---------------------------------------------------------------- ③ 판정

    private String makeJudgePrompt(String need, Map<String, List<String>> textsByPlace, Map<String, Place> candidates) {
        StringBuilder places = new StringBuilder(); // StringBuilder: 글자를 여러 번 이어 붙일 때
        for (String placeId : textsByPlace.keySet()) {
            places.append("[").append(placeId).append("] ").append(candidates.get(placeId).getName()).append("\n");
            for (String text : textsByPlace.get(placeId)) {
                places.append("- ").append(text).append("\n");
            }
        }
        return "요청: \"" + need + "\"\n"
             + "아래는 장소마다 리뷰에서 잘라 온 글이야. 장소마다 이 요청에 맞는 곳인지 판단해.\n"
             + "- ok: 리뷰가 요청에 맞는다고 말함 / bad: 리뷰가 요청에 안 맞는다고 말함 / unknown: 관련 없거나 애매함\n"
             + "- 리뷰 글에 없는 내용으로 짐작하지 마. 글이 엇갈리면 unknown.\n"
             + "- quote는 판단의 근거가 된 리뷰 글을 한 글자도 고치지 말고 그대로 복사해 (40자 이내). unknown이면 빈 글자.\n"
             + "장소:\n" + places
             + "JSON만: {\"results\": [{\"id\": \"장소 id\", \"verdict\": \"ok 또는 bad 또는 unknown\", \"quote\": \"리뷰 글 그대로\"}]}";
    }

    // ---------------------------------------------------------------- ④ 검사·반영

    /** 근거가 진짜인 판정만 반영: ok → 점수 + 리뷰 근거, bad → 후보에서 뺌. 결과를 notes에 한 줄 */
    private void applyVerdicts(String need, List<AiNeedVerdict> verdicts, Map<String, List<String>> textsByPlace,
                               Map<String, Place> candidates, Map<String, Double> scores,
                               Map<String, List<String>> reasons, List<PlanNote> notes) {
        List<String> okNames = new ArrayList<>();
        List<String> badNames = new ArrayList<>();
        for (AiNeedVerdict verdict : verdicts) {
            String placeId = verdict.getId();
            Place place = placeId == null ? null : candidates.get(placeId);
            if (place == null || !isRealQuote(verdict.getQuote(), textsByPlace.get(placeId))) {
                continue; // 후보 밖 id, 보낸 리뷰에 없는 근거는 믿지 않음
            }
            String quote = cut(verdict.getQuote().trim(), MAX_QUOTE_LENGTH);
            if (OK.equals(verdict.getVerdict())) {
                scores.put(placeId, scores.getOrDefault(placeId, 0.0) + OK_BONUS);
                addReason(reasons, placeId, need + ": 리뷰 \"" + quote + "\"");
                okNames.add(place.getName());
            } else if (BAD.equals(verdict.getVerdict())) {
                candidates.remove(placeId);
                badNames.add(place.getName() + "(\"" + quote + "\")");
            }
        }
        notes.add(makeNote(makeResultText(need, okNames, badNames)));
    }

    /** 근거 글이 보낸 리뷰 조각 안에 그대로 있는지 (띄어쓰기는 무시하고 비교) */
    private boolean isRealQuote(String quote, List<String> texts) {
        if (quote == null || texts == null) {
            return false;
        }
        String target = quote.replaceAll("\\s+", "");
        if (target.length() < MIN_QUOTE_LENGTH) {
            return false;
        }
        for (String text : texts) {
            if (text.replaceAll("\\s+", "").contains(target)) {
                return true;
            }
        }
        return false;
    }

    /** "'4명이 앉을 자리' 리뷰 확인: 맞는 곳 2곳(가게, 가게) · 뺀 곳 1곳(가게("자리가 좁아요"))" */
    private String makeResultText(String need, List<String> okNames, List<String> badNames) {
        if (okNames.isEmpty() && badNames.isEmpty()) {
            return "'" + need + "' — 리뷰만으로는 맞는지 판단할 수 있는 곳이 없었어요";
        }
        List<String> parts = new ArrayList<>();
        if (!okNames.isEmpty()) {
            parts.add("맞는 곳 " + okNames.size() + "곳(" + joinFirst(okNames) + ")");
        }
        if (!badNames.isEmpty()) {
            parts.add("뺀 곳 " + badNames.size() + "곳(" + joinFirst(badNames) + ")");
        }
        return "'" + need + "' 리뷰 확인: " + String.join(" · ", parts);
    }

    private String joinFirst(List<String> names) {
        List<String> shown = names.size() > MAX_NAMES_IN_NOTE ? names.subList(0, MAX_NAMES_IN_NOTE) : names;
        return String.join(", ", shown) + (names.size() > MAX_NAMES_IN_NOTE ? " 등" : "");
    }

    /** 리뷰 근거 목록에 한 줄 더함 (태그 근거 목록은 다른 곳에서 만든 거라 복사해서 고침) */
    private void addReason(Map<String, List<String>> reasons, String placeId, String text) {
        List<String> list = new ArrayList<>(reasons.getOrDefault(placeId, new ArrayList<>()));
        list.add(text);
        reasons.put(placeId, list);
    }

    private PlanNote makeNote(String text) {
        PlanNote note = new PlanNote();
        note.setText(text);
        return note;
    }

    private String cut(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
