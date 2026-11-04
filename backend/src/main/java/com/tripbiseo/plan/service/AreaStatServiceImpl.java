package com.tripbiseo.plan.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tripbiseo.plan.dto.AreaStat;
import com.tripbiseo.plan.dto.DongShape;
import com.tripbiseo.plan.dto.DongType;
import com.tripbiseo.plan.mapper.AreaStatMapper;

import lombok.RequiredArgsConstructor;

/** ③ 추천 지역: 우리 장소 DB에서 리뷰 수로 세어요. "요즘 인기" 같은 말을 붙일 근거는 없어서 화면도 "리뷰 많은 곳"이라고 해요 */
@Service
@RequiredArgsConstructor
public class AreaStatServiceImpl implements AreaStatService {

    private static final int AREA_COUNT_PER_KIND = 25; // 서울 구 전부 (홈 칩이 종류를 합쳐 순위를 내려고)

    private static final int MIN_PLACES = 10;        // 장소가 이보다 적으면 특색을 말할 근거가 없어요
    private static final double MIN_RATIO = 1.2;     // 서울 평균의 1.2배는 넘어야 특색으로 봐요
    private static final double TOP_RATE = 0.3;      // 그 종류 장소 수가 구 안에서 상위 30%
    private static final double FOOD_MIN_RATIO = 1.05; // 맛집은 기준을 낮춰요 (서울 전체에서 음식점 비율이 높아서)
    private static final double FOOD_TOP_RATE = 0.2;   // 또는 음식점 수가 구 안 상위 20%이면서
    private static final int FOOD_MANY = 100;          // 100곳 이상

    private final AreaStatMapper areaStatMapper;

    /** 종류마다 리뷰가 많은 순으로 구 전부 */
    @Override
    public List<AreaStat> getAreaStatList() {
        return areaStatMapper.selectAreaStatList(AREA_COUNT_PER_KIND);
    }

    /**
     * 입지계수 = (동에서 그 종류 비율) / (서울 전체에서 그 종류 비율).
     * 카페·볼거리: 1.2 이상이고 그 종류 장소 수가 구 안 동들 중 상위 30%.
     * 맛집: 서울 전체에서 음식점 비율이 높아 1.2를 넘기 어려워서, 1.05 이상이거나 "구 안 상위 20% + 100곳 이상".
     * 여러 종류가 맞으면 입지계수가 큰 쪽이 그 동의 특색
     */
    @Override
    public List<DongType> getDongTypeList(List<DongShape> dongs) {
        if (dongs == null || dongs.isEmpty()) {
            return List.of();
        }
        List<DongType> list = areaStatMapper.selectDongKindCountList(dongs);
        DongType seoul = areaStatMapper.selectSeoulKindCount();
        int seoulTotal = seoul.getFood() + seoul.getCafe() + seoul.getTour();
        List<Integer> foods = new ArrayList<>();
        List<Integer> cafes = new ArrayList<>();
        List<Integer> tours = new ArrayList<>();
        for (DongType dong : list) {
            foods.add(dong.getFood());
            cafes.add(dong.getCafe());
            tours.add(dong.getTour());
        }
        int foodCut = topCountOf(foods, FOOD_TOP_RATE);
        int cafeCut = topCountOf(cafes, TOP_RATE);
        int tourCut = topCountOf(tours, TOP_RATE);
        for (DongType dong : list) {
            int total = dong.getFood() + dong.getCafe() + dong.getTour();
            if (total < MIN_PLACES) {
                continue;
            }
            double foodRatio = ratioOf(dong.getFood(), total, seoul.getFood(), seoulTotal);
            double cafeRatio = ratioOf(dong.getCafe(), total, seoul.getCafe(), seoulTotal);
            double tourRatio = ratioOf(dong.getTour(), total, seoul.getTour(), seoulTotal);
            boolean isFood = foodRatio >= FOOD_MIN_RATIO || (dong.getFood() >= foodCut && dong.getFood() >= FOOD_MANY);
            double best = pickType(dong, "food", isFood, foodRatio, -1);
            best = pickType(dong, "cafe", cafeRatio >= MIN_RATIO && dong.getCafe() >= cafeCut, cafeRatio, best);
            pickType(dong, "tour", tourRatio >= MIN_RATIO && dong.getTour() >= tourCut, tourRatio, best);
        }
        return list;
    }

    /** 구 안 동들 중 상위 rate에 들려면 필요한 장소 수 (많은 순으로 놓고 그 자리의 값, 최소 1곳) */
    private int topCountOf(List<Integer> counts, double rate) {
        List<Integer> sorted = new ArrayList<>(counts);
        sorted.sort(Collections.reverseOrder());
        int place = (int) Math.ceil(sorted.size() * rate) - 1;
        return Math.max(1, sorted.get(place));
    }

    /** 입지계수. 서울 전체에 그 종류가 없으면 0 */
    private double ratioOf(int count, int total, int seoulCount, int seoulTotal) {
        return seoulCount == 0 ? 0 : ((double) count / total) / ((double) seoulCount / seoulTotal);
    }

    /** 기준을 넘고 입지계수가 지금까지 가장 크면 동의 특색으로 정하고, 가장 큰 값을 돌려줌 */
    private double pickType(DongType dong, String kind, boolean isQualified, double ratio, double best) {
        if (!isQualified || ratio <= best) {
            return best;
        }
        dong.setType(kind);
        return ratio;
    }
}
