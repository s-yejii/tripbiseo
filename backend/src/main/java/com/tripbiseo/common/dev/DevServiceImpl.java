package com.tripbiseo.common.dev;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.member.service.MemberService;

import com.tripbiseo.plan.service.DemoScenario;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DevServiceImpl implements DevService {

    private static final int MAX_FORWARD_MINUTES = 720; // 한 번에 12시간까지
    private static final int SECONDS_PER_MINUTE = 60;

    private final MemberService memberService;
    private final DemoScenario demoScenario; // 발표 시나리오 정의 (plan/service/DemoScenario.java)

    // 시연 계정 아이디 (쉼표로). 비어 있으면 아무도 개발자 모드를 못 씀
    @Value("${DEMO_ACCOUNTS:}")
    private String demoAccounts;

    // 회원 번호 → 시연 시계 차이(초). 서버를 다시 켜면 사라져서 진짜 시각으로 돌아옴
    // ConcurrentHashMap: 여러 요청이 동시에 읽고 써도 안전한 Map
    private final Map<Long, Long> offsets = new ConcurrentHashMap<>();

    // 회원 번호 → 시연 비 시작 시각 "13:00" (돌발 ②: 오후 비 → 아침에 알림 → 야외를 먼저)
    private final Map<Long, String> rains = new ConcurrentHashMap<>();

    @Override
    public ScenarioSetup getScenarioSetup(long memberId) {
        checkDemoAccount(memberId);
        return demoScenario.getSetup();
    }

    @Override
    public DevStatus getStatus(long memberId) {
        DevStatus status = new DevStatus();
        status.setAllowed(isDemoAccount(memberId));
        long offset = getOffsetSeconds(memberId);
        status.setOffsetMinutes(offset / SECONDS_PER_MINUTE);
        // 방금 바꾼 값도 바로 보이게 진짜 시각 + 이 회원의 차이로 계산
        status.setNow(ZonedDateTime.now(Geo.KST).plusSeconds(offset).toLocalDateTime().withSecond(0).withNano(0).toString());
        status.setRainFrom(rains.get(memberId));
        return status;
    }

    @Override
    public DevStatus setClock(long memberId, ClockRequest request) {
        checkDemoAccount(memberId);
        ZonedDateTime realNow = ZonedDateTime.now(Geo.KST);
        ZonedDateTime target = toTarget(realNow, request);
        // 초 아래(밀리초)를 올림해서 18:05로 맞추면 18:04:59가 아니라 18:05가 되게
        long millis = Duration.between(realNow, target).toMillis();
        offsets.put(memberId, Math.floorDiv(millis + 999, 1000));
        return getStatus(memberId);
    }

    @Override
    public DevStatus forwardClock(long memberId, int minutes) {
        checkDemoAccount(memberId);
        if (minutes < 1 || minutes > MAX_FORWARD_MINUTES) {
            throw fail(400, "빨리 감기는 1분부터 " + MAX_FORWARD_MINUTES + "분까지 돼요.");
        }
        offsets.put(memberId, getOffsetSeconds(memberId) + (long) minutes * SECONDS_PER_MINUTE);
        return getStatus(memberId);
    }

    @Override
    public DevStatus resetClock(long memberId) {
        offsets.remove(memberId);
        return getStatus(memberId);
    }

    @Override
    public DevStatus setRain(long memberId, String rainFrom) {
        checkDemoAccount(memberId);
        if (rainFrom == null || !rainFrom.matches("\\d{2}:\\d{2}")) {
            throw fail(400, "비 시작 시각은 13:00처럼 보내 주세요.");
        }
        rains.put(memberId, rainFrom);
        return getStatus(memberId);
    }

    @Override
    public DevStatus clearRain(long memberId) {
        rains.remove(memberId);
        return getStatus(memberId);
    }

    @Override
    public String getRainFrom(long memberId) {
        return rains.get(memberId);
    }

    @Override
    public long getOffsetSeconds(long memberId) {
        Long offset = offsets.get(memberId);
        return offset == null ? 0 : offset;
    }

    // "2026-11-07" + "18:05" → 그 시각 (날짜를 비우면 오늘)
    private ZonedDateTime toTarget(ZonedDateTime realNow, ClockRequest request) {
        try {
            LocalDate date = isBlank(request.getDate()) ? realNow.toLocalDate() : LocalDate.parse(request.getDate());
            LocalTime time = LocalTime.parse(request.getTime());
            return ZonedDateTime.of(date, time, Geo.KST);
        } catch (DateTimeParseException | NullPointerException e) {
            throw fail(400, "날짜는 2026-11-07, 시각은 18:05처럼 보내 주세요.");
        }
    }

    private void checkDemoAccount(long memberId) {
        if (!isDemoAccount(memberId)) {
            throw fail(403, "시연 계정만 쓸 수 있어요.");
        }
    }

    // 로그인한 회원의 아이디가 DEMO_ACCOUNTS 안에 있는지
    @Override
    public boolean isDemoAccount(long memberId) {
        if (isBlank(demoAccounts)) {
            return false;
        }
        String username = memberService.getMember(memberId).getUsername();
        for (String account : demoAccounts.split(",")) {
            if (account.trim().equals(username)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
