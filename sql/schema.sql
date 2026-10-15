-- EnjoyTrip 스키마
-- MySQL Workbench 에서 전체 실행하면 된다. 이미 있는 테이블은 건드리지 않는다.

CREATE DATABASE IF NOT EXISTS enjoytrip
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE enjoytrip;

-- 관광타입 (TourAPI contentTypeId)
CREATE TABLE IF NOT EXISTS content_type (
    content_type_id INT         NOT NULL,
    name            VARCHAR(20) NOT NULL,
    PRIMARY KEY (content_type_id)
);

INSERT IGNORE INTO content_type (content_type_id, name) VALUES
    (12, '관광지'),
    (14, '문화시설'),
    (15, '축제공연행사'),
    (25, '여행코스'),
    (28, '레포츠'),
    (32, '숙박'),
    (38, '쇼핑'),
    (39, '음식점');

-- 법정동 시도/시군구 코드 (ldongCode2 로 채움)
CREATE TABLE IF NOT EXISTS ldong_region (
    regn_cd   VARCHAR(5)  NOT NULL,
    regn_nm   VARCHAR(30) NOT NULL,
    signgu_cd VARCHAR(5)  NOT NULL,
    signgu_nm VARCHAR(30) NOT NULL,
    PRIMARY KEY (regn_cd, signgu_cd)
);

-- 관광 정보 (areaBasedList2 로 채움)
-- 좌표는 TourAPI 기준 mapx = 경도(longitude), mapy = 위도(latitude)
CREATE TABLE IF NOT EXISTS attraction (
    content_id      INT           NOT NULL,
    content_type_id INT           NOT NULL,
    title           VARCHAR(200)  NOT NULL,
    addr1           VARCHAR(300),
    addr2           VARCHAR(200),
    zipcode         VARCHAR(10),
    tel             VARCHAR(200),
    first_image     VARCHAR(500),
    first_image2    VARCHAR(500),
    latitude        DECIMAL(13,10) NOT NULL,
    longitude       DECIMAL(13,10) NOT NULL,
    ldong_regn_cd   VARCHAR(5),
    ldong_signgu_cd VARCHAR(5),
    lcls_systm1     VARCHAR(10),
    lcls_systm2     VARCHAR(10),
    lcls_systm3     VARCHAR(10),
    modified_time   VARCHAR(14),
    PRIMARY KEY (content_id),
    KEY idx_attraction_type (content_type_id),
    KEY idx_attraction_region (ldong_regn_cd, ldong_signgu_cd),
    KEY idx_attraction_latlng (latitude, longitude)
);

-- 저장한 여행 경로
CREATE TABLE IF NOT EXISTS plan (
    plan_id          INT          NOT NULL AUTO_INCREMENT,
    title            VARCHAR(100) NOT NULL,
    route_mode       VARCHAR(20)  NOT NULL DEFAULT 'straight',
    return_to_start  BOOLEAN      NOT NULL DEFAULT FALSE,
    total_distance_m DOUBLE       NOT NULL DEFAULT 0,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (plan_id)
);

-- 경로에 포함된 관광지와 방문 순서
CREATE TABLE IF NOT EXISTS plan_item (
    plan_id    INT NOT NULL,
    seq        INT NOT NULL,
    content_id INT NOT NULL,
    PRIMARY KEY (plan_id, seq),
    CONSTRAINT fk_plan_item_plan FOREIGN KEY (plan_id)
        REFERENCES plan (plan_id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_item_attraction FOREIGN KEY (content_id)
        REFERENCES attraction (content_id)
);
