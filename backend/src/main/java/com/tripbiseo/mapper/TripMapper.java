package com.tripbiseo.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.dto.Stop;
import com.tripbiseo.dto.Trip;

/** 여행·일정·GPS. SQL은 resources/mapper/TripMapper.xml */
@Mapper
public interface TripMapper {

    List<Trip> findByMember(long memberId);
    Trip findById(@Param("id") long id, @Param("memberId") long memberId);
    int insertTrip(Trip trip);
    int updateTrip(Trip trip);
    int updateStatus(@Param("id") long id, @Param("status") String status);
    int deleteTrip(@Param("id") long id, @Param("memberId") long memberId);

    List<Stop> findStops(long tripId);
    Stop findStop(@Param("tripId") long tripId, @Param("stopId") long stopId);
    int insertStop(Stop stop);
    int updateStop(Stop stop);
    int deleteStop(@Param("tripId") long tripId, @Param("stopId") long stopId);
    int markArrived(long stopId);
    int markDeparted(long stopId);

    int insertTrack(@Param("tripId") long tripId, @Param("lat") double lat, @Param("lng") double lng, @Param("accuracy") Double accuracy);
    List<Map<String, Object>> findTracks(long tripId);
    Map<String, Object> lastTrack(long tripId);

    int insertChat(@Param("tripId") long tripId, @Param("role") String role, @Param("text") String text, @Param("at") String at);
    List<Map<String, Object>> findChats(long tripId);
}
