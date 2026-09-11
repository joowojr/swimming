-- 폴더 고정. 고정한 시각을 남겨 "최근 고정 순"까지 정렬에 쓴다. NULL이면 고정하지 않은 폴더다.
ALTER TABLE folders ADD COLUMN pinned_at timestamptz;
