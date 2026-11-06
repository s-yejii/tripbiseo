// 시연용 데이터: 친구 소식 (친구가 다녀온 코스). friend·공유 코스 테이블이 생기면 API로 교체해요.
// 장소 이름은 우리 DB에 있는 진짜 장소예요. 친구와 다녀온 기록은 지어낸 값이에요.
export const friendNews = {
  엄마: {
    id: 'mom-1',
    date: '11/1(일)',
    area: { name: '종로구', fullName: '서울 종로구', type: 'gu', lat: 37.5729, lng: 126.9794 },
    places: ['경복궁', '세종문화회관', '미도인 그랜드센트럴', '서울역사박물관'],
  },
  민지: {
    id: 'minji-1',
    date: '10/31(토)',
    area: { name: '영등포구', fullName: '서울 영등포구', type: 'gu', lat: 37.5172, lng: 126.8956 },
    places: ['로라멘', '틈틈틈', '냐옹지마'],
  },
}
