ALTER TABLE "lr_attempts" ADD COLUMN "types" jsonb;--> statement-breakpoint
ALTER TABLE "lr_tests" ADD COLUMN "kind" text DEFAULT 'test' NOT NULL;