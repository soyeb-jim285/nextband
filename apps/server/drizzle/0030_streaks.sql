CREATE TABLE "activity_days" (
	"user_id" text NOT NULL,
	"day" date NOT NULL,
	"tests" integer DEFAULT 0 NOT NULL,
	"drills" integer DEFAULT 0 NOT NULL,
	"minutes" integer DEFAULT 0 NOT NULL,
	CONSTRAINT "activity_days_user_id_day_pk" PRIMARY KEY("user_id","day")
);
--> statement-breakpoint
CREATE TABLE "streaks" (
	"user_id" text PRIMARY KEY NOT NULL,
	"current" integer DEFAULT 0 NOT NULL,
	"longest" integer DEFAULT 0 NOT NULL,
	"last_day" date,
	"freezes" integer DEFAULT 0 NOT NULL,
	"frozen_days" date[] DEFAULT '{}'::date[] NOT NULL,
	"repair_until" timestamp with time zone,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "activity_days" ADD CONSTRAINT "activity_days_user_id_user_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."user"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "streaks" ADD CONSTRAINT "streaks_user_id_user_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."user"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
-- Backfill: each submitted speaking/writing part and each submitted Listening/Reading test (with its minutes) on its local day, so current streaks survive.
-- The day is in the student's timeZone (Asia/Dhaka when unset or unknown to Postgres). Card reviews leave no per-review log, so drills start counting from now.
INSERT INTO "activity_days" ("user_id", "day", "tests", "minutes")
SELECT x."user_id", (x."at" AT TIME ZONE coalesce(z."name", 'Asia/Dhaka'))::date, count(*), sum(x."minutes")
FROM (
  SELECT "user_id", "created_at" AS "at", 0 AS "minutes" FROM "attempts" WHERE "status" <> 'recording'
  UNION ALL
  SELECT "user_id", "submitted_at", "elapsed_s" / 60 FROM "lr_attempts" WHERE "status" = 'submitted' AND "submitted_at" IS NOT NULL
) x LEFT JOIN "user_settings" s ON s."user_id" = x."user_id"
LEFT JOIN (SELECT DISTINCT "name" FROM pg_timezone_names) z ON z."name" = s."data"->>'timeZone' -- joined once: a per-row pg_timezone_names lookup re-reads the zone files for every attempt
GROUP BY 1, 2
ON CONFLICT ("user_id", "day") DO NOTHING;
