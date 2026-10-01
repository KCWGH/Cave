# 산 봉우리 돌출

IMPLEMENTED_UNVERIFIED. 빌드·실기 확인은 사용자 수행 대기.

기존 tile_mountain.png를 참조하여 built-in image_gen으로 갈색 바위·눈 덮인 봉우리와 산기슭 바닥을 분리 제작. 생성 원본·프롬프트는 이 폴더와 generation.json에 보관한다. 원본 산 이미지는 보존한다.

tile_mountain_floor.png는140×140으로 헥사 내부에 표시. mountain_peaks.png는140×190 투명 장식으로 중심(-70,-125)에 배치한다. 꼭대기는 헥사 상단보다 최대55px 위에 나타나며 봉우리·작은 바위 전체를 헥사로 자르지 않는다. export-peaks.ps1/export-floor.ps1로 내보내기 재현.

숲과 산 장식을 한 번 r/q 순으로 정렬하고 프레임마다 공개된 장식의 전체 bounds로 컬링한다. 숲 알파 배열은 정렬된 숲 목록에 대응하고 산을 지나도 인덱스가 어긋나지 않는다. 미공개 장식은 숨긴다. 캐릭터·커서/HUD는 기존대로 위에 표시한다. 산 통행 불가·저장·전투·타이머 규칙은 유지한다. MOUNTAIN_OVERHANG_TRIAL=false로 원본 산 표시 복구 가능.

preview_map.png는 최신 숲과 나란히 놓은 PC 2배 미리보기이며 실기 화면이 아니다. 후속: 산 밀집 구역 겹침·봉우리 잘림·안개·상하 스크롤·숲 진입 전환 실기 확인 후 도시/항구 건물 장식 분리.
