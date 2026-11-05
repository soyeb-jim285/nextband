CREATE TABLE "notification_devices" (
	"token" text PRIMARY KEY NOT NULL,
	"kind" text DEFAULT 'token' NOT NULL,
	"user_id" text NOT NULL,
	"platform" text NOT NULL,
	"app_version" text,
	"locale" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"last_seen_at" timestamp with time zone DEFAULT now() NOT NULL,
	"disabled_at" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "notification_log" (
	"id" text PRIMARY KEY NOT NULL,
	"user_id" text NOT NULL,
	"type" text NOT NULL,
	"template_id" text,
	"channel" text DEFAULT 'push' NOT NULL,
	"dedupe_key" text NOT NULL,
	"status" text DEFAULT 'pending' NOT NULL,
	"title" text,
	"body" text,
	"route" text,
	"tries" integer DEFAULT 0 NOT NULL,
	"scheduled_for" timestamp with time zone DEFAULT now() NOT NULL,
	"sent_at" timestamp with time zone,
	"opened_at" timestamp with time zone,
	"acted_at" timestamp with time zone,
	"holdout" boolean DEFAULT false NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "notification_devices" ADD CONSTRAINT "notification_devices_user_id_user_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."user"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "notification_log" ADD CONSTRAINT "notification_log_user_id_user_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."user"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "notification_devices_user_idx" ON "notification_devices" USING btree ("user_id");--> statement-breakpoint
CREATE UNIQUE INDEX "notification_log_pending_key_idx" ON "notification_log" USING btree ("dedupe_key") WHERE status = 'pending';--> statement-breakpoint
CREATE INDEX "notification_log_due_idx" ON "notification_log" USING btree ("scheduled_for") WHERE status = 'pending';--> statement-breakpoint
CREATE INDEX "notification_log_user_idx" ON "notification_log" USING btree ("user_id","created_at");