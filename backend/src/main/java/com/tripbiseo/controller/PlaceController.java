package com.tripbiseo.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.service.PlaceService;

/** 장소 검색·근처·상세 (우리 DB) */
@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final PlaceService places;

    public PlaceController(PlaceService places) {
        this.places = places;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String q, @RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng) {
        return places.search(q, lat, lng);
    }

    @GetMapping("/nearby")
    public List<Map<String, Object>> nearby(@RequestParam double lat, @RequestParam double lng, @RequestParam(defaultValue = "200") int radius) {
        return places.nearby(lat, lng, radius);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        return places.detail(id);
    }
}
