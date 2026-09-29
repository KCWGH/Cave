# 원본 idle 기준 이동 스프라이트 보정

상태: REVERTED / REJECTED. 사용자가 기존 그림체로 복원을 요청하여 네 직업 모두 before/의 atlas로 복원했다. 수녀는 최초 NE/NW 다리 보정본 포함. 이 문서의 보정본/prepare 출력은 현재 게임에 적용하지 않는다.

사용자가 새 idle 후보를 거절했으므로 기존 res/warrior.png, thief.png, mage.png, nun.png를 유일한 그림체·외형 기준으로 이동 atlas를 재제작했다. 기존 이동 atlas를 함께 참조한 최초 수정은 그림체 변화가 부족하여 채택하지 않았다. 원본의 픽셀을 nearest-neighbor로 확대한 idle-reference.png만 참조한 결과를 채택했다.

- 전사: 원본 금속 신발·갑옷 질감·작은 장식·얼굴 비율.
- 도적: 원본 눈 주변·가죽 장비·짧은 망토.
- 마법사: 원본 금발·작은 어두운 신발·옷/모자 명암.
- 수녀: 원본 긴 로브·세로 주름·작은 신발·녹색 지팡이 보석. 보행용 다리 보정은 같은 외형 유지 조건으로 적용.

게임용 파일은 기존 res/{직업}_walk_trial.png에 적용한다. 420×280, 70×70셀, 열 E/NE/NW/W/SW/SE, 행 접지A/교차A/접지B/교차B, 100ms 프레임 구조를 유지한다. Java/JDP/게임 규칙은 변경하지 않는다.

## 보관 및 재현

before/에 이번 작업 전 게임 이동 atlas를 보관했다. 되돌릴 때 해당 직업 파일만 res에 복사한다. 정지 원본 해시는 idle-hashes.json에 기록했다. 각 직업-generated.png는 채택 고해상도 원본이며 PROMPTS.md에 내장 image_gen 프롬프트를 기록했다. prepare.ps1은 원본 idle 높이/발 기준선에 맞춰 nearest-neighbor로 축소하고 24프레임의 투명 여백을 검사한다. 직업-walk-refined.png를 res의 해당 파일로 복사해 적용한다.

수녀 NE/NW는 nun-rear-generated.png의 2열×4행 보정 결과를 prepare-nun-rear.ps1로 교체한다. prepare.ps1에서 자동 적용하며, 나머지 네 방향은 보정 전 refined atlas와 픽셀 동일성을 검사한다. nun-walk-refined-before-rear-fix.png는 이번 원본 그림체 보정본에서 대각선 다리 보정 직전의 중간 결과다. 기존 게임 에셋 복구에는 before/nun_walk_trial.png를 사용한다.

comparison.png는 4배 확대 비교다. 왼쪽부터 원본 idle, 보정 전 이동 SE, 보정 후 SE의 4프레임이다. 위에서 전사/도적/마법사/수녀.

## 확인

후속 전사 W/SW/SE 보행 보정은 ../warrior-gait-fix에 기록했다. 이 디렉터리의 최초 prepare.ps1 출력만 복사하면 후속 보정이 사라지므로 전사는 warrior-gait-fix/prepare.ps1의 warrior-fixed.png를 최종으로 적용한다.

96프레임의 크기·투명 경계와 idle 해시를 정적으로 검증한다. 빌드/실행은 사용자가 수행한다. 실기기에서 정지↔이동의 얼굴/크기 차이, 여섯 방향의 다리 교차(특히 수녀 NE/NW), 발/무기 잘림을 확인한다.
