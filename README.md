# 마지막 10분 (가칭)

**Pleos 차량이 주차·픽업에 걸린 시간을 직접 재고 모아, 목적지에 진짜로 도착하는 시간을 알려 주는 Pleos Connect 앱**

내비는 "목적지에 도착했습니다"에서 끝납니다. 그 뒤의 **마지막 10분**, 즉 주차장 안에서 자리 찾기, 아이 픽업, 출차를 Pleos 차량이 직접 재고, 서버가 모아 **진짜 도착 시간**을 알려줍니다. 승하차구역이 없어 이중 정차가 반복되는 곳은 **승하차 혼잡 지점**으로 자동 발견해 지자체에 승하차구역 설치 근거로 제공합니다.

현대자동차그룹 차량용 앱 개발 공모전 출품작입니다.

## 제출 정보

| 항목 | 내용 |
|---|---|
| 앱 이름 | 마지막 10분 (가칭) |
| 한 줄 소개 | Pleos 차량이 주차·픽업에 걸린 시간을 직접 재고 모아, 목적지에 진짜로 도착하는 시간을 알려 줍니다. |
| APK | [`apk/팀명_앱이름_release.apk`](apk/) (release, 서명됨) |
| 패키지 | `app.last10min`, 버전 0.1.0 (versionCode 1) |
| 개발·검증 에뮬레이터 | Pleos Connect Emulator **v2.1.0** (`RELEASE.release_connect_v2.1.0`), Android Automotive 14 (API 34), arm64 |
| 화면 규격 | **2560 × 1440**, 160 dpi, 가로 방향 |

## 구성

| 폴더 | 내용 |
|---|---|
| `app/` | Pleos Connect 차량 앱 (Kotlin, Jetpack Compose, AAOS API 34) |
| `server/` | 이벤트 집계·예측·혼잡 지점 발견 서버 (Python, FastAPI, scikit-learn) |
| `demo/scenario.py` | 에뮬레이터를 Virtual Console로 조작하는 시연 스크립트 |

### 차량 앱

- **신호 수집** (`vehicle/SignalHub.kt`)
  - 기어: Pleos Vehicle SDK `Gear`, 안 되면 AAOS `GEAR_SELECTION`
  - 좌석 착석, 도어, 안전벨트: Pleos Vehicle SDK `Seat`, `Door`
  - 차속: AAOS `PERF_VEHICLE_SPEED`, 안 되면 위치 변화로 계산
  - 위치: NaviHelper, 안 되면 Android GPS. 최근에 실제로 움직인 소스를 씁니다.
  - 지원되지 않는 신호는 "차량 신호" 탭에 빨간색으로 표시되고, 기획서 4.4·4.5의 대체 판정으로 넘어갑니다.
- **판정 엔진** (`engine/`, Android 의존성 없음, 단위 테스트 있음)
  - 주차: 진입 → 주차 완료 / 실패 → 출차
  - 승하차: 운전석은 그대로, 다른 좌석의 문과 착석만 바뀌는 경우
- **익명 업로드** (`data/EventUploader.kt`)
  - 구역 ID, 15분 단위 시간대, 소요 시간, 하루마다 바뀌는 HMAC 토큰만 보냅니다.
  - 등록된 구역 밖의 승하차는 0.0005° 격자 셀로만 보냅니다.
  - 파일 버퍼에 쌓아 두므로 시동이 꺼져도 다음 실행 때 보냅니다.
- **화면** (`ui/`): Pleos 디자인 가이드를 따랐습니다.
  - 주차: 목적지·주차 시간 드롭다운 → 가장 빨리 목적지에 도착하는 주차장, 도착 시각, 주차 요금, 주행·자리 찾기·도보 시간 막대, 가장 가까운 주차장과의 비교
  - 픽업: 목적지·도착 시간 드롭다운 → 언제 출발하면 되는지, 정차할 수 있는 곳(공식 승하차구역·주차장), 주변 혼잡 지점
  - 오른쪽 지도에 주차장(시간·요금), 승하차구역, 혼잡 지점을 표시합니다. 지도는 앱에 내장한 OSM 도로 데이터로 직접 그립니다.
  - "차량 신호" 버튼 뒤에 신호값·데이터 출처·판정 엔진 상태를 모아 두었습니다(심사 시연용).
  - 컬러 토큰, Asta Sans 서체, Page Navigation, Tabs(Box), 버튼 눌림 축소 효과(95%), 라이트/다크 테마, 1:1·세로 창 대응
  - 정차 위치는 공식 승하차구역과 주차장만 안내하고, 혼잡 지점은 정보로만 보여 줍니다(기획서 5.3).
- **오프라인 데모**: 서버에 연결되지 않아도 같은 화면이 나오도록 데이터를 앱에 내장했습니다(release 빌드 기본값).
  - `assets/zones.json`: 주차장(요금표 포함)과 공식 승하차구역
  - `assets/demo_snapshot.json`: 서버 예측(구역 × 평일/주말 × 15분)과 발견된 혼잡 지점의 스냅샷
  - `assets/roads.json`: 강남역·대치동 일대 도로(OpenStreetMap)
  - 순서: 서버 응답 → 내장 스냅샷 → 1단계 사전값

### 서버

| 엔드포인트 | 설명 |
|---|---|
| `POST /v1/events` | 익명 이벤트 수신. 엄격하게 검증하고, 잘못된 항목은 번호와 사유를 돌려줍니다. |
| `GET /v1/zones` | 주차장, 공식 승하차구역, 발견된 혼잡 지점(`guidance: false`) |
| `GET /v1/predictions?zoneIds=&offsets=` | 3단계 예측: 사전값 → 누적 패턴(구역 × 평일/주말 × 15분) → 최근 30분 실시간 보정. k-익명성 기준(주차 5대, 승하차 10대)에 못 미치면 사전값을 씁니다. |
| `GET /v1/hotspots` | 승하차 혼잡 지점. 셀 밀도 기준과 DBSCAN으로 찾고, 서로 다른 차량 10대 이상·서로 다른 날 5일 이상 반복된 곳만 남깁니다. |
| `GET /v1/profile?zoneId=&kind=` | 구역의 시간대별(24시간) 예측과 기록 수. 대시보드 그래프용 |
| `GET /` | 데이터 흐름 대시보드(세로 화면): 차량 안의 신호 → 서버로 보낸 익명 이벤트 → 집계 → 예측·혼잡 지점 |
| `/demo/*` | 시연 전용. `L10_DEMO=1`로 실행했을 때만 열립니다. |
| `GET /docs` | API 문서(Swagger) |

시연 전용 기능(`L10_DEMO=1`)은 두 가지입니다. 디버그 빌드 앱이 차량 상태(기어, 차속, 문, 좌석, 판정 상태)를 1초마다 보내 대시보드 맨 위 칸에 표시합니다. 이 값은 메모리에만 두고 DB에 저장하지 않으며, release 빌드는 보내지 않습니다. 대시보드의 시나리오 버튼은 서버를 실행한 PC에서 `demo/scenario.py`를 실행합니다(`adb` 필요).

## 실행

### release APK로 바로 실행 (서버 불필요)

release 빌드는 서버에 연결하지 않고, 앱에 내장한 구역·예측·도로 데이터로 주차와 픽업 화면을 모두 보여 줍니다.

```bash
# Pleos Connect Emulator v2.1.0을 켠 뒤
adb install -r -g apk/팀명_앱이름_release.apk   # -g: 위치 권한을 설치 때 허용
adb shell am start -n app.last10min/.MainActivity
```

- 앱 목록에서 **마지막 10분**을 눌러도 됩니다.
- 같은 패키지의 debug 빌드가 이미 설치되어 있으면 서명이 달라 설치가 거부됩니다. `adb uninstall app.last10min`으로 먼저 지우세요.
- 화면: **주차** 탭(목적지·주차 시간 → 가장 빨리 도착하는 주차장), **픽업** 탭(목적지·도착 시간 → 출발 시각, 정차할 수 있는 곳), **차량 신호**(신호값·판정 상태).

### 서버와 함께 실행 (debug 빌드, 개발·시연용)

필요한 것은 Android Studio, JDK 21, `uv`(Python), 그리고 Pleos Connect Emulator(`Pleos_Connect_v2.1.0`, API 34)입니다.

```bash
# 1) 서버: 28일치 합성 데이터를 넣고 8080 포트로 실행
cd server
uv sync
uv run python -m last10.seed 28
L10_DEMO=1 uv run uvicorn last10.main:app --host 0.0.0.0 --port 8080   # L10_DEMO=1: 시연 전용 기능 켜기

# 2) 앱: 에뮬레이터를 켠 뒤 설치 (debug 빌드는 10.0.2.2:8080 서버에 연결)
cd ..
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew :app:installDebug
adb shell pm grant app.last10min android.permission.ACCESS_FINE_LOCATION   # 또는 설치 시 -g

# 3) 시연: 주차 → 픽업 → 혼잡 지점 후보
python3 demo/scenario.py parking     # all, pickup, hotspot도 가능
# 또는 http://localhost:8080 대시보드의 시나리오 버튼
```

내장 데모 데이터를 다시 만들 때는 다음을 실행합니다.

```bash
cd server && uv run python -m last10.snapshot ../app/src/main/assets/demo_snapshot.json   # 서버 DB → 예측·혼잡 지점 스냅샷
cd .. && python3 tools/fetch_roads.py app/src/main/assets/roads.json                        # Overpass API → 도로 데이터 (한 번만)
```

Android Studio에서 열 때는 **Settings → Build Tools → Gradle → Gradle JDK**를 **JDK 21**로 지정하세요. 기본 JBR 25는 Gradle 8.14와 맞지 않습니다.

release APK를 직접 빌드하려면 저장소 루트에 `keystore.properties`(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`)를 두고 `./gradlew :app:assembleRelease`를 실행합니다. 이 파일은 git에 올리지 않으며, 없으면 debug 키로 서명된 release APK가 만들어집니다.

테스트는 다음 명령으로 돌립니다.

```bash
./gradlew :app:testDebugUnitTest      # 판정 엔진, 익명화, 추천 로직
cd server && uv run pytest            # 수신 검증, 예측, 혼잡 지점 발견
```

## 테스트 환경 및 필요한 설정

| 항목 | 내용 |
|---|---|
| 개발 PC | macOS 26.6 (Apple M4 Pro) |
| 에뮬레이터 | Pleos Connect Emulator v2.1.0, Android Automotive 14 (API 34), arm64, 2560 × 1440 / 160 dpi, 가로 |
| 빌드 | JDK 21 (Temurin), Gradle 8.14.3, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compileSdk·targetSdk 34, minSdk 28 |
| 주요 라이브러리 | Pleos Vehicle SDK 2.3.2, NaviHelper 3.0.5 (`nexus-playground.pleos.ai`), Jetpack Compose BOM 2024.10.01 |
| 서버(선택) | Python 3.12 이상, `uv`, FastAPI. release APK 실행에는 필요 없음 |

- **위치 권한**: `ACCESS_FINE_LOCATION`이 필요합니다. `adb install -g`로 설치하거나 첫 실행 때 허용하세요.
- **차량 신호 권한**: `CAR_DRIVE`, `CAR_SEATS`, `CAR_DOORS`는 에뮬레이터에서는 동작을 확인했고, 실차 배포에는 Pleos App Market의 사전 승인이 필요합니다.
- **에뮬레이터 차량 상태 조작**: 주차·승하차 판정을 직접 확인하려면 에뮬레이터의 Virtual Console에서 기어·문·좌석을 바꾸거나, `demo/scenario.py`를 실행하세요(`adb` 필요).
- **네트워크**: release APK는 네트워크 없이 동작합니다. debug 빌드는 `10.0.2.2:8080`(에뮬레이터에서 본 개발 PC)의 서버에 연결합니다.

## 에뮬레이터에서 확인한 사실과 대응

| 항목 | 확인 결과 | 대응 |
|---|---|---|
| Vehicle SDK 기어, 좌석, 도어, 벨트 | 콜백 수신 확인 | 주 신호원으로 사용 |
| AAOS `PERF_VEHICLE_SPEED` | 수신 확인 | 주 신호원으로 사용 |
| `SEAT_OCCUPANCY` 값 | 에뮬레이터의 SDK가 VACANT를 `true`로 넘김 | 주행 중(D, 5km/h 이상)에는 운전석에 사람이 있어야 한다는 사실로 극성을 한 번 학습해 보정. 신호 탭에 표시 |
| NaviHelper 현재 위치 | 내비 앱 자체 위치(판교)에 고정, `geo fix`를 따르지 않음 | 위치 소스를 "최근에 움직인 쪽"으로 자동 선택하고, 수동 전환도 가능 |
| Virtual Console | 브라우저 세션 heartbeat가 있어야 쓰기 가능 | 시연 스크립트가 2초마다 heartbeat를 보냄 |
| NaviHelper `requestRoute` | `NAVI_ROUTE` 외에 `pleos.car.permission.NAVI_ROUTE_SEARCH` 권한 필요 | 매니페스트에 선언. 목적지 변경 후 내비 앱이 안내를 시작하는 것까지 확인 |

## 알려진 한계

- Vehicle SDK 권한(`CAR_DRIVE`, `CAR_SEATS`, `CAR_DOORS`)은 Pleos App Market의 사전 승인이 필요합니다.
- 시동이 꺼진 뒤 백그라운드에서 계속 실행되는지는 실차에서 검증하지 못했습니다. 이벤트는 파일 버퍼에 남아 있다가 다음 실행 때 전송됩니다.
- 예측 1단계의 공공데이터(서울시 실시간 주차정보) 연동은 아직 하지 않았습니다. 지금은 `zones.json`의 `capacity`/`available` 값으로 사전값을 계산합니다.
- 다른 주차장으로 갈 때의 주행 시간은 직선거리 × 1.3 ÷ 시속 25km로, 도보는 직선거리 × 1.25 ÷ 분속 75m로 추정합니다. NaviHelper는 현재 목적지의 ETA만 줍니다.
- 주차 요금은 데모용 예시 데이터입니다.
- 내비 앱의 지도는 앱 안에 띄울 수 없습니다(NaviHelper에 지도 View가 없음). 실제 경로 안내는 "여기로 안내"로 내비 앱에 넘깁니다.
- OSM 타일 서버는 앱의 직접 사용과 대량 다운로드를 막고 있어서 타일 대신 도로 데이터를 받아 직접 그립니다.

## 라이선스

- Asta Sans 서체: SIL Open Font License 1.1 (`app/src/main/assets/licenses/AstaSans-OFL.txt`)
- 지도 도로 데이터: © OpenStreetMap contributors, ODbL 1.0 (지도 왼쪽 아래에 표기)
