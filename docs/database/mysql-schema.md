# MySQL 스키마

## user-service DB: `gymplan_user`

```sql
CREATE TABLE users (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  email       VARCHAR(255) UNIQUE NOT NULL,
  password    VARCHAR(255) NOT NULL,       -- BCrypt hash
  nickname    VARCHAR(50)  NOT NULL,
  profile_img VARCHAR(500),
  status      ENUM('ACTIVE','INACTIVE','BANNED') DEFAULT 'ACTIVE',
  created_at  DATETIME DEFAULT NOW(),
  updated_at  DATETIME DEFAULT NOW() ON UPDATE NOW()
);
```

## plan-service DB: `gymplan_plan`

```sql
CREATE TABLE workout_plans (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT NOT NULL,
  name        VARCHAR(100) NOT NULL,       -- ex: '가슴/삼두 루틴'
  description TEXT,
  day_of_week TINYINT,                     -- 0=월~6=일, NULL=무요일
  is_template BOOLEAN DEFAULT FALSE,
  is_active   BOOLEAN DEFAULT TRUE,
  created_at  DATETIME DEFAULT NOW(),
  updated_at  DATETIME DEFAULT NOW() ON UPDATE NOW(),
  INDEX idx_user_day (user_id, day_of_week)
);

CREATE TABLE plan_exercises (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_id       BIGINT NOT NULL,
  exercise_id   BIGINT NOT NULL,
  exercise_name VARCHAR(100) NOT NULL,     -- 비정규화: exercise-catalog HTTP 호출 없이 조회
  muscle_group  VARCHAR(50)  NOT NULL,     -- 비정규화: CHEST|BACK|SHOULDERS|ARMS|LEGS|CORE|CARDIO
  order_index   INT NOT NULL,
  target_sets   INT DEFAULT 3,
  target_reps   INT DEFAULT 10,
  target_weight DECIMAL(5,2),              -- kg
  rest_seconds  INT DEFAULT 90,
  notes         VARCHAR(255),
  INDEX idx_plan (plan_id)
);
```

## exercise-catalog DB: `gymplan_exercise`

```sql
CREATE TABLE exercises (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  name         VARCHAR(100) NOT NULL,
  name_en      VARCHAR(100),
  muscle_group ENUM('CHEST','BACK','SHOULDERS','ARMS','LEGS','CORE','CARDIO'),
  equipment    ENUM('BARBELL','DUMBBELL','MACHINE','CABLE','BODYWEIGHT','BAND'),
  difficulty   ENUM('BEGINNER','INTERMEDIATE','ADVANCED'),
  description  TEXT,
  video_url    VARCHAR(500),
  is_custom    BOOLEAN DEFAULT FALSE,
  created_by   BIGINT,
  created_at   DATETIME DEFAULT NOW(),
  FULLTEXT INDEX ft_name (name, name_en)
);
```

## 인덱스 전략

- `workout_plans`: `(user_id, day_of_week)` 복합 인덱스 → 오늘의 루틴 조회 최적화
- `plan_exercises`: `plan_id` 단일 인덱스 → 루틴 상세 조회
- `exercises`: `FULLTEXT (name, name_en)` → 종목 검색 (Elasticsearch 보조)
