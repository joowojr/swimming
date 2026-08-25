CREATE TABLE `users` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `email` varchar(255) UNIQUE NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `nickname` varchar(255) NOT NULL,
  `timezone` varchar(255) NOT NULL DEFAULT 'Asia/Seoul',
  `created_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  `updated_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE `project_tags` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(30) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  `updated_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE `projects` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `tag_id` bigint,
  `name` varchar(255) NOT NULL,
  `description` text,
  `target_date` date,
  `status` varchar(255) NOT NULL DEFAULT 'IN_PROGRESS',
  `is_deleted` boolean NOT NULL DEFAULT false,
  `created_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  `updated_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE `tasks` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `project_id` bigint,
  `source_note_id` bigint,
  `title` varchar(255) NOT NULL,
  `status` varchar(255) NOT NULL DEFAULT 'TODO',
  `order_idx` int NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  `updated_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE `daily_plans` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `plan_date` date NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  `updated_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE `daily_plan_items` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `daily_plan_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `order_idx` int NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  `updated_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE `group_rooms` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `place_id` bigint NOT NULL,
  `started_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  `duration_sec` int NOT NULL,
  `capacity` int NOT NULL DEFAULT 50,
  `host_type` varchar(255) NOT NULL DEFAULT 'SYSTEM',
  `status` varchar(255) NOT NULL DEFAULT 'SCHEDULED',
  `created_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  `updated_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE `sessions` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `active_user_id` bigint UNIQUE,
  `type` varchar(255) NOT NULL,
  `place_id` bigint NOT NULL,
  `group_room_id` bigint,
  `music_url` varchar(2048),
  `planned_duration_sec` int NOT NULL,
  `actual_duration_sec` int,
  `started_at` timestamp NOT NULL,
  `end_at` timestamp,
  `status` varchar(255) NOT NULL,
  `summary` varchar(255),
  `created_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  `updated_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE `session_tasks` (
  `session_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `order_idx` int NOT NULL,
  PRIMARY KEY (`session_id`, `order_idx`),
  UNIQUE (`session_id`, `task_id`)
);

CREATE TABLE `notes` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `content` text NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `is_deleted` boolean NOT NULL DEFAULT false,
  `context_type` varchar(20) NOT NULL,
  `project_id` bigint,
  `session_id` bigint,
  `created_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  `updated_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE `group_participants` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `group_room_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `task_id` bigint,
  `ad_hoc_goal` varchar(255),
  `joined_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  `created_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  `updated_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE `cities` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `country_code` varchar(255) NOT NULL,
  `timezone` varchar(255) NOT NULL DEFAULT 'Asia/Seoul',
  `created_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  `updated_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE `places` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `city_id` bigint NOT NULL,
  `name` varchar(255) NOT NULL,
  `background_asset_type` varchar(255) NOT NULL,
  `background_asset_key` varchar(2048) NOT NULL,
  `default_music_url` varchar(2048),
  `created_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  `updated_at` timestamp NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE UNIQUE INDEX `daily_plans_index_0` ON `daily_plans` (`user_id`, `plan_date`);

CREATE UNIQUE INDEX `project_tags_user_name_index` ON `project_tags` (`user_id`, `name`);

CREATE INDEX `idx_projects_user_status_deleted_created_at` ON `projects` (`user_id`, `status`, `is_deleted`, `created_at`);

CREATE UNIQUE INDEX `daily_plan_items_index_1` ON `daily_plan_items` (`daily_plan_id`, `task_id`);

CREATE INDEX `idx_tasks_user_id` ON `tasks` (`user_id`);

CREATE INDEX `sessions_index_2` ON `sessions` (`user_id`, `started_at`);

CREATE INDEX `sessions_index_3` ON `sessions` (`user_id`, `group_room_id`, `started_at`);

CREATE INDEX `sessions_index_4` ON `sessions` (`group_room_id`, `started_at`);

CREATE UNIQUE INDEX `group_participants_index_5` ON `group_participants` (`group_room_id`, `user_id`);

CREATE INDEX `idx_notes_user_status_created_at` ON `notes` (`user_id`, `status`, `created_at`);

CREATE INDEX `idx_notes_user_project_status_created_at` ON `notes` (`user_id`, `project_id`, `status`, `created_at`);

CREATE INDEX `idx_notes_user_session_status_created_at` ON `notes` (`user_id`, `session_id`, `status`, `created_at`);

ALTER TABLE `project_tags` ADD FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `projects` ADD FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `projects` ADD FOREIGN KEY (`tag_id`) REFERENCES `project_tags` (`id`);

ALTER TABLE `tasks` ADD FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `tasks` ADD FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`);

ALTER TABLE `tasks` ADD FOREIGN KEY (`source_note_id`) REFERENCES `notes` (`id`);

ALTER TABLE `daily_plans` ADD FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `daily_plan_items` ADD FOREIGN KEY (`daily_plan_id`) REFERENCES `daily_plans` (`id`) ON DELETE CASCADE;

ALTER TABLE `daily_plan_items` ADD FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`) ON DELETE CASCADE;

ALTER TABLE `group_rooms` ADD FOREIGN KEY (`place_id`) REFERENCES `places` (`id`);

ALTER TABLE `sessions` ADD FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `session_tasks` ADD FOREIGN KEY (`session_id`) REFERENCES `sessions` (`id`) ON DELETE CASCADE;

ALTER TABLE `session_tasks` ADD FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`);

ALTER TABLE `notes` ADD FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `notes` ADD FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`);

ALTER TABLE `notes` ADD FOREIGN KEY (`session_id`) REFERENCES `sessions` (`id`);

ALTER TABLE `sessions` ADD FOREIGN KEY (`place_id`) REFERENCES `places` (`id`);

ALTER TABLE `sessions` ADD FOREIGN KEY (`group_room_id`) REFERENCES `group_rooms` (`id`);

ALTER TABLE `group_participants` ADD FOREIGN KEY (`group_room_id`) REFERENCES `group_rooms` (`id`);

ALTER TABLE `group_participants` ADD FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `group_participants` ADD FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`);

ALTER TABLE `places` ADD FOREIGN KEY (`city_id`) REFERENCES `cities` (`id`);
