-- 데일리 플랜 항목의 수동 순서를 없앤다.
--
-- 화면에 순서를 바꾸는 기능이 없고, 목록 조회(findRows)도 이미 created_at 순으로 읽어
-- order_idx를 보지 않았다. 게다가 항목을 지워도 번호를 다시 매기지 않아 빈 번호가 생기고,
-- 새 항목을 "현재 개수"로 채번하면 남아 있는 항목과 같은 번호가 될 수 있었다.
-- 순서는 이제 id(담은 순)가 정한다.
ALTER TABLE daily_plan_items DROP COLUMN order_idx;
