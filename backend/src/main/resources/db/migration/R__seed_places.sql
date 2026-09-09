-- place 기준 데이터. 사용자 데이터가 아니라 앱이 동작하려면 있어야 하는 값이다.
--
-- 버전 마이그레이션이 아니라 repeatable(R__)인 이유:
-- 이 파일은 "한 번 실행할 변경"이 아니라 "카탈로그의 현재 상태"다. 내용이 바뀌면
-- Flyway 가 다시 실행하므로, place 를 추가하거나 이름을 고칠 때 새 V 파일을 만들지
-- 않고 여기만 고치면 된다. 그래서 모든 INSERT 는 반드시 멱등이어야 한다.
--
-- 행을 지워도 DB 에서 삭제되지는 않는다. sessions·group_rooms 가 place_id 를
-- 참조하므로 place 삭제는 별도 판단이 필요한 작업이고, 자동으로 지우면 위험하다.

-- background_asset_key, thumbnail_asset_key 컬럼에 media/ 접두사를 넣지 않는다.
-- 넣으면 경로가 두 번 붙어 404 가 난다.
--
-- 파일 이름 끝의 032e53e2 는 파일 내용의 sha256 앞 8 자리다.
-- 영상 교체는 "같은 키에 덮어쓰기"가 아니라 "새 키로 올리고 이 값을 바꾸기"다.
--
-- 업로드는 infra/scripts/upload-media.sh 가 해시 계산과 헤더 지정을 대신하고,
-- 여기에 넣을 키를 출력한다.

INSERT INTO cities (id, name, country_code, timezone) VALUES
  (1, 'Lisbon', 'PT', 'Europe/Lisbon'),
  (2, 'New York', 'US', 'America/New_York')
ON CONFLICT (id) DO UPDATE SET
  name         = EXCLUDED.name,
  country_code = EXCLUDED.country_code,
  timezone     = EXCLUDED.timezone,
  updated_at   = CURRENT_TIMESTAMP;

INSERT INTO places (
  id, city_id, name, background_asset_type, background_asset_key, thumbnail_asset_key
) VALUES
  (1, 1, 'Café da Garagem', 'VIDEO', 'cities/videos/places/1_lisbon_1.032e53e2.mp4', 'cities/thumbnails/places/1_lisbon_1.4bd716d1.mp4'),
  (2, 1, 'Dear Breakfast', 'VIDEO', 'cities/videos/places/1_lisbon_2.e14c2531.mp4', 'cities/thumbnails/places/1_lisbon_2.653b8179.mp4'),
  (3, 1, 'A Cafe', 'VIDEO', 'cities/videos/places/1_lisbon_3.59b316ba.mp4', 'cities/thumbnails/places/1_lisbon_3.8e17444a.mp4'),
  (4, 2, 'New York', 'VIDEO', 'cities/videos/places/2_newyork_4.c2dc3343.mp4', 'cities/thumbnails/places/2_newyork_4.1242456d.mp4')
ON CONFLICT (id) DO UPDATE SET
  city_id               = EXCLUDED.city_id,
  name                  = EXCLUDED.name,
  background_asset_type = EXCLUDED.background_asset_type,
  background_asset_key  = EXCLUDED.background_asset_key,
  thumbnail_asset_key   = EXCLUDED.thumbnail_asset_key,
  updated_at            = CURRENT_TIMESTAMP;

-- id 를 직접 지정하므로 identity 시퀀스가 따라오지 않는다. 맞춰 두지 않으면
-- 다음에 id 없이 INSERT 할 때 1 부터 발급해 기본키 충돌이 난다.
SELECT setval(pg_get_serial_sequence('cities', 'id'), (SELECT max(id) FROM cities));
SELECT setval(pg_get_serial_sequence('places', 'id'), (SELECT max(id) FROM places));
