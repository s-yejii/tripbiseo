package com.tripbiseo.after.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.after.dto.AlbumResponse;
import com.tripbiseo.after.dto.Photo;
import com.tripbiseo.after.dto.PhotoResponse;

/** ⑤ 사진·영상 (photo). SQL은 resources/mapper/after/PhotoMapper.xml */
@Mapper
public interface PhotoMapper {

    /*사진 한줄 저장 */
    int insertPhoto(Photo photo);

    List<PhotoResponse> selectPhotoList(long tripId); // 한 여행의 사진 영상, 찍은순 (화면에 보냉 모양)

      /** 내 여행의 사진이면 한 줄, 남의 사진이면 null (파일 보내 주기 전 확인) */
    Photo selectPhotoFile(@Param("memberId") long memberId, @Param("photoId") long photoId);

   
    List<AlbumResponse> selectAlbumList(long memberId);  /* 내 지난 여행 목록 + 장소 수 사진수 영상수 (포토즈는 서비스가 채움 : 여행 한번 돌때마다 사진 목록을 다 넣음. 서비스가) */ 
     // 여행들이 앨범에 모인 목록
}
