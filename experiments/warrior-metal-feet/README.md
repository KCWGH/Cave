# 전사 발 갑옷 금속색 복원

상태: IMPLEMENTED_UNVERIFIED. 사용자가 지적한 원본 idle의 금속색 발 갑옷을 이동 atlas에도 적용했다. 빌드·실기기 확인은 사용자 담당.

res/warrior_walk_trial.png만 변경. 얼굴 시안은 미적용이며, 원본 idle와 다른 세 직업은 해시 보존 확인했다.

내장 image_gen으로 고해상도 atlas의 갈색 발만 금속색으로 수정했다. PROMPT.md에 전체 프롬프트, generated.png에 출력 보관. 전체 생성 이미지를 게임에 덮어쓰지 않았다.

prepare.ps1은 기존 atlas 발 영역의 갈색 픽셀 연결 성분만 선택하여 수정 소스의 금속색 RGB를 통합한다. 소스에서 대응 금속 픽셀이 없는 경우 기존 픽셀 명암을 강철색 ramp로 변환한다. alpha와 검은 외곽선은 보존한다. 발의 위치·모양·크기·걷기 프레임은 기존 그대로다.

검증: 24프레임의 발색1920픽셀만 변경. 전체 alpha 및 발 마스크 밖 모든 픽셀 동일성 검사. 정지 원본/다른 직업 해시와 게임 적용 파일 해시 검사. Java/JDP 변경 없음. 얼굴 시안의 보행 기준 사본도 같은 금속색 에셋으로 갱신하여 얼굴 시안 재현 시 갈색 발로 되돌아가지 않게 했다.

warrior-before.png: 색 변경 전 복구본. warrior-metal-feet.png: 적용본. comparison.png: 원본 idle / 갈색 발 보정 전 / 금속색 보정 후, 4배 확대.
