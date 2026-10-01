# 승인된 최종 적용 — 2026-10-01

IMPLEMENTED_UNVERIFIED. 사용자 승인: preview_dark_forest.png 기준. forest_dense_dark.png/forest_clearing_dark.png를 게임의 숲 두 상태로 적용. 기존140×190·중심(-70,-115)·약500ms 전환 유지, RGB만90%로 낮춤. 원본과 알파 차이0 확인. mountain_peaks.png는140×210·중심(-70,-140)으로 적용하여 높이/대비 강화. 바닥·통행·저장 규칙 유지. darken-forest.ps1/preview-dark-forest.ps1로 색감과 배치 재현.

이하 낮은 숲/공통 레이어 등 이전 제안은 적용 대상 아님.

사용자 거절 시안 — 게임 적용 금지. 현재 숲 기준은 forest-occupancy-v1 (2026-10-01).

# 숲·산 존재감 조정 시안

PREVIEW_ONLY. 게임 코드/res는 변경하지 않았다.

built-in image_gen으로 기존 숲 두 상태의 밝은 노란 하이라이트를 낮추고 짧고 둥근 수관으로 수정했다. 산은 중앙 주봉을 높이고 눈과 바위 골의 대비를 강화했다. 원본 생성물을 *_source.png로 보존.

export.ps1은 알파16 초과 범위를 찾고 숲140×170, 산140×210에 전체 모습을 여백 포함 축소한다. 숲 배치(-70,-100), 산(-70,-140): 위쪽 돌출 약30/70px. 바닥과 헥사 규격은 유지한다. preview.ps1은 실제 배치·정렬로 만든 2배 PC 미리보기다. BlackBerry 실행 화면이 아니다.

후속: 시안 평가 후 두 숲 PNG·산 PNG와 렌더링 앵커 적용, 점유 전환/파티 공간/밀집 산·숲·안개·스크롤 실기 확인.
