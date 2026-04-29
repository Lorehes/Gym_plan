-- GymPlan 로컬 개발용 DB 초기화 스크립트
-- 실행 시점: MySQL 컨테이너 최초 기동 (데이터 디렉토리가 비어 있을 때)
-- docker-compose.local.yml > mysql > volumes > /docker-entrypoint-initdb.d/
--
-- 주의: MYSQL_DATABASE 환경변수로 이미 gymplan_user 가 생성되므로
--       CREATE DATABASE IF NOT EXISTS 로 멱등성을 보장한다.

-- ─────────────────────────────────────────────
-- 1. 서비스별 DB 생성
-- ─────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS gymplan_user
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS gymplan_plan
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS gymplan_exercise
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- 2. gymplan 사용자 권한 부여
-- ─────────────────────────────────────────────
GRANT ALL PRIVILEGES ON gymplan_user.*     TO 'gymplan'@'%';
GRANT ALL PRIVILEGES ON gymplan_plan.*     TO 'gymplan'@'%';
GRANT ALL PRIVILEGES ON gymplan_exercise.* TO 'gymplan'@'%';
FLUSH PRIVILEGES;

-- ─────────────────────────────────────────────
-- 3. gymplan_user 스키마
-- ─────────────────────────────────────────────
USE gymplan_user;

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    email       VARCHAR(255) UNIQUE NOT NULL,
    password    VARCHAR(255) NOT NULL,
    nickname    VARCHAR(50)  NOT NULL,
    profile_img VARCHAR(500),
    status      ENUM('ACTIVE','INACTIVE','BANNED') NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL
);

-- ─────────────────────────────────────────────
-- 4. gymplan_plan 스키마
-- ─────────────────────────────────────────────
USE gymplan_plan;

CREATE TABLE IF NOT EXISTS workout_plans (
    id          BIGINT      PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    day_of_week INT,
    is_template BOOLEAN     NOT NULL DEFAULT FALSE,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    INDEX idx_user_day (user_id, day_of_week)
);

CREATE TABLE IF NOT EXISTS plan_exercises (
    id            BIGINT      PRIMARY KEY AUTO_INCREMENT,
    plan_id       BIGINT      NOT NULL,
    exercise_id   BIGINT      NOT NULL,
    exercise_name VARCHAR(100) NOT NULL,
    muscle_group  VARCHAR(50) NOT NULL,
    order_index   INT         NOT NULL,
    target_sets   INT         NOT NULL DEFAULT 3,
    target_reps   INT         NOT NULL DEFAULT 10,
    target_weight DECIMAL(5,2),
    rest_seconds  INT         NOT NULL DEFAULT 90,
    notes         VARCHAR(255),
    INDEX idx_plan (plan_id)
);

-- ─────────────────────────────────────────────
-- 5. gymplan_exercise 스키마
-- ─────────────────────────────────────────────
USE gymplan_exercise;

CREATE TABLE IF NOT EXISTS exercises (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
    name         VARCHAR(100) NOT NULL,
    name_en      VARCHAR(100),
    muscle_group VARCHAR(50)  NOT NULL,
    equipment    VARCHAR(50)  NOT NULL,
    difficulty   VARCHAR(20)  NOT NULL,
    description  TEXT,
    video_url    VARCHAR(500),
    is_custom    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by   BIGINT,
    created_at   DATETIME(6)  NOT NULL,
    FULLTEXT INDEX ft_name (name, name_en)
);
