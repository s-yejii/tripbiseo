package com.tripbiseo.controller;

import static com.tripbiseo.common.ApiException.fail;

import java.security.Principal;
import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.service.LiveService;

/** 여행 중 화면. 모든 요청에 지금 위치(lat, lng)를 함께 보냄 */
@RestController
@RequestMapping("/api/trips/{id}/live")
public class LiveController {

    private final LiveService live;

    public LiveController(LiveService live) {
        this.live = live;
    }

    @PostMapping("/track")
    public Map<String, Object> track(@PathVariable long id, @RequestBody Map<String, Object> b, Principal user) {
        return live.track(user, id, num(b, "lat"), num(b, "lng"), b.get("accuracy") instanceof Number n ? n.doubleValue() : null);
    }

    @PostMapping("/status")
    public Map<String, Object> status(@PathVariable long id, @RequestBody Map<String, Object> b, Principal user) {
        return live.status(user, id, num(b, "lat"), num(b, "lng"));
    }

    @PostMapping("/assist")
    public Map<String, Object> assist(@PathVariable long id, @RequestBody Map<String, Object> b, Principal user) {
        return live.assist(user, id, (String) b.get("message"), num(b, "lat"), num(b, "lng"));
    }

    @PostMapping("/recommend")
    public Map<String, Object> recommend(@PathVariable long id, @RequestBody Map<String, Object> b, Principal user) {
        return live.recommend(user, id, num(b, "lat"), num(b, "lng"), (int) num(b, "radius"), (String) b.get("category"));
    }

    @PostMapping("/insert")
    public Map<String, Object> insert(@PathVariable long id, @RequestBody Map<String, Object> b, Principal user) {
        return live.insert(user, id, (String) b.get("placeId"), num(b, "lat"), num(b, "lng"));
    }

    @PostMapping("/alternatives")
    public Map<String, Object> alternatives(@PathVariable long id, @RequestBody Map<String, Object> b, Principal user) {
        return live.alternatives(user, id, num(b, "lat"), num(b, "lng"), String.valueOf(b.getOrDefault("reason", "delay")), (String) b.get("request"));
    }

    @PostMapping("/apply")
    public Map<String, Object> apply(@PathVariable long id, @RequestBody Map<String, Object> b, Principal user) {
        return live.apply(user, id, b, num(b, "lat"), num(b, "lng"));
    }

    private static double num(Map<String, Object> b, String key) {
        if (!(b.get(key) instanceof Number n)) throw fail(400, key.equals("lat") || key.equals("lng") ? "내 위치를 먼저 찾아 주세요." : key + " 값이 필요해요.");
        return n.doubleValue();
    }
}
