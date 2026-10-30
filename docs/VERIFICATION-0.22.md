# MapExit 0.22 검증

## 빌드와 호환성

- 기준 소스: `17d620f8628499cbab5467b544c4bbc06e3e1bc7` (0.16). 이후 미보관 기능을 재구현했습니다.
- JDK 17.0.16, Gradle 8.11.1, Android SDK 35, Build Tools 35.0.0.
- `:core:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` 성공.
- 코어 테스트 175개 통과, 실패 0개. Android lint 오류 0개.
- `tools/build_pixel_sprites.py --check` 및 `tools/check_weapon_balance.py --check` 통과.
- applicationId `com.powerfulhimchan.mapexit`, versionCode 22, versionName 0.22.0.
- 개발 인증서 SHA-256: `9756aecf15a972bb7340bdf2d96780568853addaec5d094496dd8a5f5ffa2264`. 이전 0.17~0.21과 동일합니다.
- 기존 저장 버전 1 유지. ELITE 열거형을 마지막에 추가하고 새 필드에 기본값을 두었습니다.

## 코어 회귀 범위

- 네 방향과 가능한 모든 갈림길 조합에서 왼쪽/오른쪽 버튼 배치, 제자리 뒤로 돌기.
- 칼날 앞의 몸 전체 출발 위치, 단조로운 이동, 중앙 착지와 점프 종료.
- 직선 시야의 시설, 벽 가림, 해결된 시설 제거, 접근 카메라의 확대 역전 방지.
- 5층 단위 정예의 출구 인접 위치, 층당 상자 2개, 보상·보스 별도 처리 및 예전 맵의 정예 보완.
- 아홉 장비 부위의 호환 목록·직접 장착, 상자/전투/상점 획득 장비, 양손 장비 교체와 저장 복원.
- 같은 종류 장비의 불이익·상한 이전 능력치·반지 선택 부위 비교, 구매/취소·수량·골드·중복 ID 방지.
- 레벨업 후 성장 메뉴를 열고 저장/복원해도 승리 결과 유지, 획득 경험치와 필요 경험치.
- 이동 이후 완료 설명 초기화, 무기별 매 프레임 자세 연속성과 타격 이전 피격 효과 방지.

## 안드로이드 실행 검증

Android 11(API 30), x86_64 소프트웨어 에뮬레이터에 APK와 테스트 APK를 설치했습니다.
`RestoredRenderingTest`의 아래 3개 계측 테스트가 435.786초에 통과했습니다.

- `allWeaponsAndEnemyActionsRenderDuringContactAndRecovery`: 다섯 무기, 일반/강공격, 보스 다섯 행동과 두 화면 높이. 실제 Activity에서 경험치·상점·구매·레벨업·성장 화면 전환도 수행합니다.
- `bladesAndEveryOtherObstacleLandAtTheCenter`: 다섯 장애물의 준비/활성/성공·실패 중간/착지, 높이 200/640.
- `fixturesUsePerspectiveAndRespectOcclusionAndResolution`: 거리별 문·회복·상자·상인·정예·함정, 해결/벽 가림과 다섯 테마.

총 181개 장면 렌더링과 실제 메뉴 6개 캡처를 생성했습니다. 장면은 불투명도와 게임 상태 비변경을 검사하고, 시설은 실제 픽셀의 제거·가림을 비교합니다.
첫 캡처 추출은 Android 외부 폴더 접근 제한으로 실패했습니다. 앱 내부 폴더와 debug run-as 추출로 변경한 뒤 `reviewRepresentativeScenes`를 따로 실행해 203.033초에 통과했고, 대표 장면 24장과 메뉴 6장 및 추가 Activity 캡처를 가져왔습니다.

직접 확인한 장면에서 문·회복 제단의 바닥 접지, 거리별 크기 변화, 칼날 앞 출발·중앙 착지, 솥·벽·나무봉 결과의 중앙 위치, 높이 200/640의 보스 자세를 확인했습니다. 큰 화면 보스 준비 자세의 해머 원화 좌표를 실제 회전으로 변환해 오른쪽 끝이 452.65px 미만(폭 480px)인 것도 확인했습니다.

실제 Activity 메뉴 캡처에는 소프트웨어 에뮬레이터의 ‘System UI isn’t responding’ 대화상자가 겹쳤으므로, 이를 가림 없는 메뉴 시각 검증의 증거로 사용하지 않습니다. 게임 Activity의 메뉴 상태 전환과 성장 화면 도달은 계측 코드에서 완료했습니다.

기존 `LaunchTest` 전체 UI 시나리오와 실제 기기의 프레임 속도 벤치마크는 이번 검증 범위에 포함하지 않았습니다.
