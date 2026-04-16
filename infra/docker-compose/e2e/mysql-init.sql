-- E2E 테스트용 MySQL 초기화
-- MYSQL_DATABASE=gymplan_user 로 1차 생성, 여기서 나머지 추가
-- MYSQL_USER 에게 모든 서비스 DB 접근 권한 부여

CREATE DATABASE IF NOT EXISTS gymplan_plan     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS gymplan_exercise CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- docker-compose MYSQL_USER 는 최초 MYSQL_DATABASE 에만 ALL PRIVILEGES 를 갖는다.
-- 추가 DB 에도 동일 권한을 부여한다.
GRANT ALL PRIVILEGES ON gymplan_plan.*     TO 'gymplan'@'%';
GRANT ALL PRIVILEGES ON gymplan_exercise.* TO 'gymplan'@'%';
FLUSH PRIVILEGES;
