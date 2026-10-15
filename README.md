# EnjoyTrip (Algorithm PJT)

공공데이터(한국관광공사 TourAPI)를 활용한 여행 경로 추천 서비스.
프레임워크 없이 Java 표준 라이브러리와 JDBC(MySQL)만으로 만든다.

## 개발 환경

- Java 17 / STS (또는 Eclipse), VS Code
- MySQL 8 / MySQL Workbench

## 처음 실행할 때

1. MySQL JDBC 드라이버를 받아서 `lib/` 에 넣는다.
   - https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar
   - 파일 이름을 바꾸지 않는다. (`.classpath` 가 이 이름을 참조함)
2. `config/app.properties.example` 을 `config/app.properties` 로 복사하고 DB 계정과 API 키를 채운다.
3. STS에서 `File > Import > Existing Projects into Workspace` 로 이 폴더를 연다.
4. `com.ssafy.enjoytrip.Main` 을 실행한다.

## 폴더 구조

```
config/   설정 파일 (app.properties 는 커밋하지 않음)
lib/      외부 jar (커밋하지 않음)
sql/      DB 스키마
src/      애플리케이션 소스
test/     테스트, 성능 측정 코드
web/      화면 (HTML, CSS, JS)
```
