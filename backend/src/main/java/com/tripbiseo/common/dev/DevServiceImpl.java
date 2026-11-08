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
    private static final int MAX_SPEED = 120; // 시계 배속 최대 (진짜 1초에 2분)

    private final MemberService memberService;
    private final DemoScenario demoScenario; // 발표 시나리오 정의 (plan/service/DemoScenario.java)

    // 시연 계정 아이디 (쉼표로). 비어 있으면 아무도 개발자 모드를 못 씀
    @Value("${DEMO_ACCOUNTS:}")
    private String demoAccounts;

    // 회원 번호 → 시연 시계 차이(초). 서버를 다시 켜면 사라져서 진짜 시각으로 돌아옴
    // ConcurrentHashMap: 여러 요청이 동시에 읽고 써도 안전한 Map
    private final Map<Long, Long> offsets = new ConcurrentHashMap<>();

    // 회원 번호 → 시계 배속 (0 멈춤, 1 보통, 30이면 진짜 1초에 30초). 배속을 정한 진짜 시각(초)부터 그만큼 빨리 흘러요 (11/08)
    private final Map<Long, Integer> speeds = new ConcurrentHashMap<>();
    private final Map<Long, Long> speedFrom = new ConcurrentHashMap<>();

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
        status.setSpeed(speeds.getOrDefault(memberId, 1));
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
        speedFrom.put(memberId, realSeconds()); // 배속은 이 시각부터 다시 셈
        return getStatus(memberId);
    }

    @Override
    public DevStatus forwardClock(long memberId, int minutes) {
        checkDemoAccount(memberId);
        if (minutes < 1 || minutes > MAX_FORWARD_MINUTES) {
            throw fail(400, "빨리 감기는 1분부터 " + MAX_FORWARD_MINUTES + "분까지 돼요.");
        }
        keepOffset(memberId);
        offsets.put(memberId, offsets.get(memberId) + (long) minutes * SECONDS_PER_MINUTE);
        return getStatus(memberId);
    }

    @Override
    public DevStatus resetClock(long memberId) {
        offsets.remove(memberId);
        speeds.remove(memberId);
        speedFrom.remove(memberId);
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
        long base = offset == null ? 0 : offset;
        Integer speed = speeds.get(memberId);
        if (speed == null || speed == 1) {
            return base;
        }
        // 배속을 정한 뒤 진짜로 흐른 시간 × (배속 - 1)만큼 더 감. 0배속(멈춤)이면 흐른 만큼 빼서 제자리
        long passed = realSeconds() - speedFrom.getOrDefault(memberId, realSeconds());
        return base + passed * (speed - 1);
    }

    @Override
    public DevStatus setSpeed(long memberId, int speed) {
        checkDemoAccount(memberId);
        if (speed < 0 || speed > MAX_SPEED) {
            throw fail(400, "배속은 0(멈춤)부터 " + MAX_SPEED + "배까지 돼요.");
        }
        keepOffset(memberId);
        speeds.put(memberId, speed);
        return getStatus(memberId);
    }

    // 지금까지 배속으로 흐른 만큼을 고정된 차이로 굳히고, 배속은 지금부터 다시 셈 (배속·빨리 감기를 바꾸기 전에)
    private void keepOffset(long memberId) {
        offsets.put(memberId, getOffsetSeconds(memberId));
        speedFrom.put(memberId, realSeconds());
    }

    private long realSeconds() {
        return System.currentTimeMillis() / 1000;
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
