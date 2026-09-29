# 정지 에셋 그림체 통일 후보

상태: REJECTED. 사용자 거절: 새 idle 후보 대신 원본 idle에 이동 스프라이트를 맞추기로 결정. 기존 res 에셋과 코드/JDP는 변경하지 않았다. 보정 작업은 ../walk-idle-match에 기록한다.

네 직업의 채택 이동 atlas를 그림체·캐릭터 기준으로, 기존 정지 에셋을 정면 대기 자세 참고로 사용했다. 내장 image_gen으로 직업별 한 장씩 생성했다. PROMPTS.md에 전체 프롬프트를 기록했다.

- warrior/thief/mage/nun-generated.png: 투명 고해상도 후보.
- 각 직업-idle-candidate.png: 70×70 투명 후보. 이동 atlas SE 두 번째 프레임의 높이·발 기준선에 맞춤.
- comparison.png: 왼쪽 기존 정지, 가운데 새 후보, 오른쪽 이동 참조. 위에서 전사/도적/마법사/수녀. 4배 nearest-neighbor 확대.
- prepare.ps1: 크기 정렬과 비교 이미지 재현. 이미지 파일 처리만 수행하며 게임 빌드/실행은 하지 않음.

채택 시 게임에 적용할 범위(지도 정지/전투/HUD)를 확정하고 기존 원본을 보존한 채 적용한다.
