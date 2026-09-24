-- 캘린더 날짜를 task의 속성으로 옮긴다. 한 task는 최대 하나의 날짜를 갖는다.
-- docs/backlog/task-owns-plan-date.md

ALTER TABLE tasks ADD COLUMN plan_date date;

-- 여러 날짜에 담긴 task는 가장 늦은 날짜 하나만 남긴다. 나머지 날짜의 기록은 옮기지 않는다.
UPDATE tasks
SET plan_date = latest.plan_date
FROM (
    SELECT task_id, max(plan_date) AS plan_date
    FROM daily_plan_items
    GROUP BY task_id
) AS latest
WHERE tasks.id = latest.task_id;

CREATE INDEX tasks_user_plan_date_index ON tasks (user_id, plan_date);

DROP TABLE daily_plan_items;
