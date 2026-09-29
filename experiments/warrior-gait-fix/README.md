# 전사 W / SW / SE 다리 교차 보정

상태: REVERTED / REJECTED. 사용자 평가 후 이전 그림체로 복원했다. res/warrior_walk_trial.png는 ../walk-idle-match/before/warrior_walk_trial.png와 동일하다. 이 디렉터리의 warrior-fixed.png를 다시 적용하지 않는다. 다른 직업 보정도 진행하지 않는다.

문제: 기존 W/SW/SE는 접지 A와 B에서 같은 발을 앞에 두는 모습이 반복됐다. 사용자에게 정상으로 확인된 E/NE/NW는 그대로 보존했다.

## 적용

- res/warrior_walk_trial.png만 교체. 420×280, 6방향×4프레임, 100ms 순환 유지. Java/JDP 변경 없음.
- W: 정상 E 보행을 참고해 image_gen으로 만든 왼쪽 보행 하체를 네 프레임에 통합.
- SW/SE: 기존 접지 A/교차 A는 유지. 생성한 반대 접지/교차 하체를 좌우 반사해 앞발 위치가 A와 반대로 바뀌도록 배치.
- 얼굴·상체와 손/방패는 기존 픽셀 유지. 하체 통합은 셀 y49 이상·x23~48에서 장비 보호 영역을 제외한다. prepare.ps1에 정확한 범위 기록.

전체 이미지 생성은 반대 앞발을 안정적으로 바꾸지 못해 채택하지 않았다. 내장 image_gen으로 만든 방향별 하체 소스를 게임 atlas에 통합했다. PROMPTS.md에 채택 소스 프롬프트와 통합 설명을 기록했다.

## 파일 / 재현

- warrior-before.png: 이번 수정 전 게임 atlas, 복구하려면 res/warrior_walk_trial.png에 복사.
- generated-w.png, generated-front-opposite.png: 채택 소스.
- prepare.ps1: 축소/프레임 통합, 24셀 투명 경계 및 정상 방향·상체·SW/SE 앞 두 프레임 보존 검사.
- warrior-fixed.png: 적용한 최종 atlas.
- preview.ps1, frame-0~3.png, walk.gif: 최종 게임 atlas의 W/SW/SE를 4배 확대한 100ms 보행 미리보기. 게임 실행 아님.
- unchanged-hashes.json: 원본 warrior idle 및 다른 세 직업 이동 atlas의 보존 기준.

검증: SW의 아래쪽 금속 발 중심은 접지 A/B에서 x31.90→36.40, SE는 x35.05→32.92로 반대 방향 이동한다(셀 y56~59 금속색 픽셀 기준). 이 수치는 축소 후 차이가 남는다는 보조 검사이며 보행 품질 판정은 실기기 확인이 필요하다. W는 네 프레임의 무릎·발 앞뒤 자세를 시각 확인했다. 빌드/설치/게임 실행은 하지 않았다.
