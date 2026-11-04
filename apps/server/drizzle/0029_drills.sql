ALTER TABLE "cards" ADD COLUMN "category" text;--> statement-breakpoint
ALTER TABLE "cards" ADD COLUMN "attempt_id" text;--> statement-breakpoint
ALTER TABLE "cards" ADD COLUMN "suspended" boolean DEFAULT false NOT NULL;--> statement-breakpoint
CREATE INDEX "cards_user_cat_idx" ON "cards" USING btree ("user_id","category");--> statement-breakpoint
-- Backfill: a mistake card (front = the original text) takes the category and test of its mistake; Listening/Reading spelling cards are 'spelling'.
UPDATE "cards" c SET "category" = m."category", "attempt_id" = m."attempt_id" FROM "mistakes" m WHERE c."source" = 'mistake' AND c."category" IS NULL AND m."user_id" = c."user_id" AND m."original" = c."front";--> statement-breakpoint
UPDATE "cards" SET "category" = 'spelling' WHERE "source" = 'mistake' AND "category" IS NULL AND ("front" LIKE '🎧 Listening%' OR "front" LIKE '📖 Reading%');
--> statement-breakpoint
-- Listening spelling cards take the newest Listening attempt where that word was misspelt, so "Hear the word" can play it from the recording.
UPDATE "cards" c SET "attempt_id" = (
  SELECT a."id" FROM "lr_attempts" a JOIN "lr_tests" t ON t."id" = a."test_id"
  WHERE a."user_id" = c."user_id" AND t."skill" = 'listening'
    AND EXISTS (SELECT 1 FROM jsonb_array_elements(a."analysis"->'gaps') g WHERE g->>'word' = split_part(c."back", E'\n', 1))
  ORDER BY a."submitted_at" DESC NULLS LAST LIMIT 1
) WHERE c."category" = 'spelling' AND c."attempt_id" IS NULL AND c."front" LIKE '🎧%';
