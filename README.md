# EnjoyTrip (Algorithm PJT)

공공데이터(한국관광공사 TourAPI)를 활용한 여행 경로 추천 서비스.
프레임워크 없이 Java 표준 라이브러리와 JDBC(MySQL)만으로 만들었다.

## 주요 기능

| 기능 | 적용 알고리즘 / 자료구조 | 위치 |
|---|---|---|
| 여행 경로 방문 순서 추천 | 비트마스크 DP (TSP), 지점이 많으면 최근접 이웃 + 2-opt | `algo/tsp` |
| 관광지 이름 자동완성 | 트라이 + 노드별 top-K 캐시, 초성 검색 | `algo/search` |
| 지도 영역 마커, 주변 관광지 | 위경도 격자 공간 인덱스, 하버사인 거리 | `algo/geo` |
| 랜덤 경로 | 반경 5km 후보 + 부분 Fisher-Yates 무작위 선택 + TSP | `algo/route`, `service/RouteService` |

- 관광지 탐색 페이지: 지도 이동 시 화면 안의 관광지를 관광타입별 색으로 표시, 검색, 상세 정보, 경로에 담기
- 여행 경로 페이지: 검색이나 지도에서 관광지 선택, 출발지/도착지/복귀 옵션, 경로 생성, 저장/불러오기/삭제
- 랜덤 경로: 사용자가 고른 관광지(최소 1곳) 중 첫 번째 곳 기준 반경 5km 안에서 무작위로 골라 경로 생성.
  후보가 요청 개수보다 적으면 있는 만큼만 고른다.
- 경로 가중치는 지금은 좌표 간 직선거리(하버사인). `DistanceProvider` 인터페이스로 분리해 두어서
  카카오 길찾기 API 구현체를 추가하면 설정만 바꿔 교체할 수 있다.

성능 측정 결과는 [docs/performance.md](docs/performance.md) 참고.

## 개발 환경

- Java 17 이상 / STS (또는 Eclipse), VS Code
- MySQL 8 / MySQL Workbench

## 처음 실행할 때

1. MySQL JDBC 드라이버를 받아서 `lib/` 에 넣는다.
   - https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar
   - 파일 이름을 바꾸지 않는다. (`.classpath` 가 이 이름을 참조함)
2. `config/app.properties.example` 을 `config/app.properties` 로 복사하고 DB 계정과 API 키를 채운다.
   - `datagokr.key`: 공공데이터포털 인증키 (한국관광공사 국문 관광정보 서비스 활용신청 필요)
   - `kakao.js.key`: 카카오 JavaScript 키. 비워 두면 지도 없이 목록으로만 동작한다.
     카카오 개발자 콘솔 > 앱 > 플랫폼 > Web 에 `http://localhost:8080` 을 등록해야 지도가 뜬다.
3. STS에서 `File > Import > General > Existing Projects into Workspace` 로 이 폴더를 연다.
4. MySQL Workbench 에서 `sql/schema.sql` 을 실행한다.
5. `com.ssafy.enjoytrip.external.DataLoader` 를 실행해서 TourAPI 데이터를 DB 에 넣는다.
   - 약 5만 건, API 호출 60회 정도, 2~3분 걸린다.
   - 개발계정은 하루 1,000건 제한이 있으니 여러 번 돌리지 않는다. 다시 돌려도 중복 없이 덮어쓴다.
6. `com.ssafy.enjoytrip.Main` 을 실행하고 브라우저에서 http://localhost:8080 을 연다.

실행 위치(작업 디렉터리)가 프로젝트 루트여야 `config/`, `web/` 폴더를 찾는다. STS 기본 설정이 그렇다.

## 테스트와 성능 측정

JUnit 없이 main 메서드로 돈다. 실패하면 `[실패]` 줄이 찍히고 종료 코드 1 로 끝난다.

| 클래스 | 내용 | DB 필요 |
|---|---|---|
| `algo.TspTest` | 무작위 입력 200개에서 DP 두 가지와 완전탐색 결과 비교, 출발/도착/복귀 옵션 | X |
| `algo.SearchTest` | 초성 변환, 트라이 접두어/초성/단어 검색, top-K | X |
| `algo.GeoIndexTest` | 격자 인덱스와 전체 순회 결과 비교, 무작위 선택 균등성 | X |
| `bench.TspBenchmark` | 완전탐색, 상향식/하향식 DP, 2-opt 시간 비교 | X |
| `bench.SearchBenchmark` | 트라이 vs 전체 순회, 격자 vs 전체 순회 | O |

## API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/config` | 화면 설정 (카카오 JS 키 등) |
| GET | `/api/content-types` | 관광타입 목록 |
| GET | `/api/regions` | 시도/시군구 목록 |
| GET | `/api/attractions/suggest?q=&limit=` | 자동완성 |
| GET | `/api/attractions/search?q=&types=&regnCd=&limit=` | 이름 검색 (부분 일치) |
| GET | `/api/attractions/bounds?swLat=&swLng=&neLat=&neLng=&types=&limit=` | 지도 영역 안의 관광지 |
| GET | `/api/attractions/nearby?lat=&lng=&radius=&types=&limit=` | 주변 관광지 (가까운 순) |
| GET | `/api/attractions?ids=` | 여러 건 기본 정보 |
| GET | `/api/attractions/{contentId}` | 상세 (개요는 TourAPI 에서 받아 캐시) |
| POST | `/api/routes/optimize` | 경로 생성 `{contentIds, startId, endId, returnToStart}` |
| POST | `/api/routes/random` | 랜덤 경로 `{baseIds, count, types, seed}` |
| GET | `/api/plans` | 저장된 경로 목록 |
| POST | `/api/plans` | 경로 저장 `{title, routeMode, returnToStart, contentIds}` |
| GET | `/api/plans/{planId}` | 경로 불러오기 |
| DELETE | `/api/plans/{planId}` | 경로 삭제 |

## 폴더 구조

```
config/   설정 파일 (app.properties 는 커밋하지 않음)
docs/     성능 측정 결과 등 문서
lib/      외부 jar (커밋하지 않음)
sql/      DB 스키마
src/      애플리케이션 소스
  algo/       알고리즘 (tsp, search, geo, route)
  dao/        JDBC 데이터 접근
  external/   TourAPI 클라이언트, 데이터 적재
  service/    비즈니스 로직
  web/        HTTP 서버, 라우터, API
test/     테스트, 성능 측정 코드
web/      화면 (HTML, CSS, JS)
```
