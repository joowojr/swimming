-- F07 세션 공간·음악 URL 스키마 전환
-- 대상: MySQL 8.4
-- 전제: Hibernate ddl-auto=update가 신규 컬럼(updated_at, background_asset_type,
--       default_music_url, music_url)을 추가한 현재 로컬 DB에서 한 번만 실행한다.
-- 주의: 공간이 연결되지 않은 기존 세션과 해당 세션의 하위 기록을 삭제한다.

START TRANSACTION;

DELETE FROM check_ins
WHERE session_id IN (
    SELECT id
    FROM sessions
    WHERE place_id IS NULL
);

DELETE FROM session_tasks
WHERE session_id IN (
    SELECT id
    FROM sessions
    WHERE place_id IS NULL
);

DELETE FROM sessions
WHERE place_id IS NULL;

COMMIT;

ALTER TABLE cities
    MODIFY COLUMN updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE places
SET background_asset_type = 'VIDEO'
WHERE background_asset_type IS NULL;

UPDATE places
SET default_music_url = default_music_ref
WHERE default_music_url IS NULL
  AND default_music_ref IS NOT NULL;

ALTER TABLE places
    MODIFY COLUMN background_asset_type varchar(255) NOT NULL,
    MODIFY COLUMN background_asset_url varchar(2048) NOT NULL,
    MODIFY COLUMN default_music_url varchar(2048),
    DROP COLUMN default_music_ref;

ALTER TABLE sessions
    MODIFY COLUMN place_id bigint NOT NULL,
    MODIFY COLUMN music_url varchar(2048),
    DROP COLUMN task_id;

SELECT COUNT(*) AS sessions_without_place
FROM sessions
WHERE place_id IS NULL;

SHOW COLUMNS FROM cities;
SHOW COLUMNS FROM places;
SHOW COLUMNS FROM sessions;
