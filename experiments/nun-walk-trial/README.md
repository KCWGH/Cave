# Nun 6방향 걷기

상태: IMPLEMENTED_UNVERIFIED. 사용자 수동 빌드·실기기 평가 대기.

- 원본 nun.png의 복장/지팡이 스타일을 참고한 6방향×4프레임 이동 atlas.
- 게임 리소스: res/nun_walk_trial.png, 420×280 투명 PNG, 셀70×70.
- 열 순서: E, NE, NW, W, SW, SE. 행: 접지/교차/반대 접지/반대 교차. 기존 공통 코드로 100ms마다 교대(순환400ms).
- 원본 크기와 발 기준선에 맞춰 정렬. 원본/정지/전투/HUD와 다른 직업은 유지.
- built-in image_gen 제작. PROMPTS.md에 전체 프롬프트, generated-atlas.png에 채택 고해상도 출력, prepare.ps1에 셀 정렬/축소·투명 경계 검증 기록.
- 검증: 24셀 모두 채워짐/투명 경계/게임 규격, 원본 보존, 소스 구조/인코딩/JDP 등록 정적 확인. 빌드·게임 실행은 하지 않음.

## 실기기 확인

NE/NW 보행 보정: 로브 아래 양발을 드러내고 1/3행의 앞발·뒷발을 서로 바꿨다. 2/4행은 반대 지지발의 교차 자세다. 다른 네 방향은 픽셀 그대로 보존했다. 채택 원본 rear-diagonal-generated.png, 프롬프트 PROMPTS-rear-fix.md, 재적용 prepare-rear-fix.ps1. 최초 prepare.ps1 실행 후에는 이 보정 스크립트를 실행해야 현재 결과가 재현된다. 보정 전 atlas는 nun-walk-before-rear-fix.png에 보관했다.

Space로 Nun 턴까지 넘긴 뒤 6방향과 꺾이는 경로를 이동한다. 양발 교차, 지팡이/발 잘림, 정지↔이동 크기 변화, 조우 도망 후 재개, 백그라운드 복귀를 확인한다.

## 되돌리기

Unit.java의 NUN_WALK_TRIAL=false로 해당 직업만 비활성화할 수 있다. 전사/도적 및 다른 직업 플래그는 유지한다. before에 변경 전 Unit.java/Cave.jdp/원본 에셋 보관. 두 직업 추가 전 스냅샷이므로 이후 변경이 있으면 전체 덮어쓰기 대신 해당 직업 로딩/플래그/JDP 리소스만 선택적으로 revert한다.
