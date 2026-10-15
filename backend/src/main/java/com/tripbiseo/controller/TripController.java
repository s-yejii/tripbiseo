package com.tripbiseo.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.dto.Stop;
import com.tripbiseo.dto.Trip;
import com.tripbiseo.service.TripService;

/** 여행·일정 조회/추가/수정/삭제 (로그인한 회원 것만) */
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService trips;

    public TripController(TripService trips) {
        this.trips = trips;
    }

    @GetMapping
    public List<Trip> list(Principal user) { return trips.list(user); }

    @PostMapping
    public Trip create(@RequestBody Trip draft, Principal user) { return trips.create(user, draft); }

    @GetMapping("/{id}")
    public Trip get(@PathVariable long id, Principal user) { return trips.get(user, id); }

    @PutMapping("/{id}")
    public Trip update(@PathVariable long id, @RequestBody Trip info, Principal user) { return trips.update(user, id, info); }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id, Principal user) {
        trips.delete(user, id);
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/start")
    public Trip start(@PathVariable long id, Principal user) { return trips.start(user, id); }

    @PostMapping("/{id}/finish")
    public Trip finish(@PathVariable long id, Principal user) { return trips.finish(user, id); }

    @PostMapping("/{id}/stops")
    public Trip addStop(@PathVariable long id, @RequestBody Stop stop, Principal user) { return trips.addStop(user, id, stop); }

    @PutMapping("/{id}/stops/{stopId}")
    public Trip updateStop(@PathVariable long id, @PathVariable long stopId, @RequestBody Stop stop, Principal user) {
        return trips.updateStop(user, id, stopId, stop);
    }

    @DeleteMapping("/{id}/stops/{stopId}")
    public Trip deleteStop(@PathVariable long id, @PathVariable long stopId, Principal user) { return trips.deleteStop(user, id, stopId); }

    @GetMapping("/{id}/chats")
    public List<Map<String, Object>> chats(@PathVariable long id, Principal user) { return trips.chats(user, id); }

    @PostMapping("/{id}/chats")
    public Map<String, Object> addChats(@PathVariable long id, @RequestBody List<Map<String, Object>> messages, Principal user) {
        return trips.addChats(user, id, messages);
    }

    @GetMapping("/{id}/retro")
    public Map<String, Object> retro(@PathVariable long id, Principal user) { return trips.retro(user, id); }
}
