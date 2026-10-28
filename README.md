# MapExit 0.5.0

버튼 두 개로 탐험하는 오프라인 안드로이드 도트 던전 게임. Kotlin 게임 로직과 Compose/Canvas 화면을 분리했습니다.

## 주요 기능

- 가로 480도트의 1인칭 탐험/3인칭 전투·조우, 32×48 캐릭터와 26종의 세밀한 도트 원화
- 원근에 맞춘 벽·바닥 재질, 균열·이끼·횃불, 출정 준비·계승·함정 화면과 장비 아이콘
- 작은 주변 미니맵, 상태 요약, 상황별 A/B 직접 행동
- 터치 방식 출정 준비: 장비 장착·비교, 소모품 수량 구매, 능력치 성장·골드 보관
- 통로 자동 이동, 갈림길 선택, 재도전마다 새로 생성되는 1~50층 던전
- 상자·NPC·상인의 전용 조우 화면, NPC의 단일 의뢰 수락/거절, 상자 몬스터 20% 확률
- 함정의 1초 자동 안내·약 2초 입력·결과 자동 복귀, 판매 목록의 장착 표시와 판매 즉시 제거
- 몬스터·상자·회복 장소·상인·미션 NPC·출구·다섯 함정
- 공격/방어 교대, 일반·강공격, 방어·회피, 회복약, 도주
- 무기 5종, 손 점유 규칙과 장비 슬롯 9칸, 반지의 능력치 보정과 목걸이의 이벤트 효과
- 레벨업 후 능력치 직접 배분, 골드 보관, 귀환·사망·자동 저장
- 10층마다 보스와 이동 장치, 50층 클리어 후 기존 계승 장비와 신규 한 개를 보존하는 새 회차
- 일반 진행과 분리된 개발용 테스트 모드

Android 8.0(API 26) 이상에서 실행합니다. 인터넷 권한·계정·서버가 필요 없습니다. 조작과 테스트 모드는 [첫 플레이 가이드](docs/PLAYTEST.md)를 참고하세요.

## 빌드

JDK 17, Android SDK 35와 Build Tools 35.0.0을 설치하고 Android Studio에서 열거나 실행합니다.

```sh
./gradlew :core:test :app:lintDebug :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

에뮬레이터 또는 연결된 테스트 기기가 있으면 다음 명령으로 앱 실행 검증을 진행합니다.

```sh
./gradlew :app:connectedDebugAndroidTest
```

서명 키는 저장소에 포함하지 않습니다. 로컬 개발 키가 있으면 사용하고, 없으면 Android 기본 debug 서명을 사용합니다. 같은 인증서의 APK로 업데이트할 때 데이터를 유지할 수 있습니다. [서명 설정](dev-signing/README.md)을 참고하세요. release 배포 서명은 별도 설정합니다.

## 구조와 수치 수정

| 위치 | 역할 |
|---|---|
| core | 안드로이드 의존성 없는 게임 상태·규칙·저장 직렬화와 자동 테스트 |
| app | Compose/Canvas 화면, 480도트 장면·재질·원화, A/B 입력, 기기 내부 저장 |
| tools/build_pixel_sprites.py | 26종 도트 원화의 팔레트·직접 그린 좌표와 Kotlin atlas 생성/검사 |
| balance/weapons.json | 앱과 Python 검증이 공유하는 무기·전투 수치 |
| tools/check_weapon_balance.py | 독립 무기 밸런스 검증 모델 |

```sh
python3 tools/check_weapon_balance.py --check
python3 tools/build_pixel_sprites.py --check
```

[게임 설계](docs/game-design.md), [무기 밸런스 초안](docs/weapon-balance.md), [수정·플레이 가이드](docs/PLAYTEST.md), [검증 결과](docs/VERIFICATION-0.5.md)를 함께 관리합니다. GitHub Actions에서도 core 테스트·Android lint·APK 빌드를 실행하고 APK를 아티팩트로 보관합니다.

층당 약 5분과 후반부 난이도는 실제 플레이로 조정할 목표입니다. 현재 무기 검증 모델은 반지·목걸이, 연속 탐험과 전체 층 성장 곡선을 포함하지 않습니다.
