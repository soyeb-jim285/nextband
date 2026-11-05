// Push notification copy (docs/engagement/notifications-and-streaks.md §2, §4, §5.5, §5.6). Written by coding agents, reviewed in PRs.
// Every push goes through checkPush before it is sent: push never sells (the Play build is consumption-only) and never shames.

/** Android notification channels (§6). The app creates them; a push names one in its data payload. */
export const PUSH_CHANNELS = ['results', 'streak', 'practice', 'exam', 'weekly'] as const;
export type PushChannel = (typeof PUSH_CHANNELS)[number];

export type PushLanguage = 'en' | 'banglish';
/** Round B types: #1 result ready, #2 marking failed, #10 personal best, and the Settings test push. */
export type PushType = 'result' | 'failed' | 'best' | 'test';
export const PUSH_TYPE_CHANNEL: Record<PushType, PushChannel> = { result: 'results', failed: 'results', best: 'results', test: 'results' };

type Template = { id: string; title: string; body: string };
// Variables: {what} "Writing Task 2" / "Speaking test", {band} "6.5", {skill} "Speaking".
export const PUSH_TEMPLATES: Record<PushType, Record<PushLanguage, Template[]>> = {
  result: {
    en: [
      { id: 'result-en-1', title: 'Your {what} got {band} 🎯', body: 'See the one thing that held you back.' },
      { id: 'result-en-2', title: '{what}: band {band}', body: 'Your feedback is ready. Tap to see what to fix first 🎯' },
    ],
    banglish: [
      { id: 'result-bn-1', title: 'Tomar {what} e {band} 🎯', body: 'Kon jinish ta pichone tene rakhse, dekhe nao.' },
      { id: 'result-bn-2', title: '{what} er band: {band}', body: 'Feedback ready. Age ki thik korte hobe dekho 🎯' },
    ],
  },
  failed: {
    en: [
      { id: 'failed-en-1', title: "We couldn't mark your {what}", body: "Tap to try again. It didn't use up a test." },
      { id: 'failed-en-2', title: "Marking didn't finish", body: "Your {what} is safe. Tap to try again, it won't use a test." },
    ],
    banglish: [
      { id: 'failed-bn-1', title: 'Tomar {what} mark kora jayni', body: 'Abar try korte tap koro. Kono test kata jayni.' },
      { id: 'failed-bn-2', title: 'Marking shesh hoyni', body: 'Tomar {what} safe ache. Abar try koro, test katbe na.' },
    ],
  },
  best: {
    en: [
      { id: 'best-en-1', title: 'New best: {band} in {skill} 🚀', body: 'Your highest {skill} band yet. See what worked this time.' },
      { id: 'best-en-2', title: '{band} in {skill}, your best yet', body: 'Tap to see what you did differently this time 🚀' },
    ],
    banglish: [
      { id: 'best-bn-1', title: 'Notun best: {skill} e {band} 🚀', body: 'Ekhon porjonto tomar shobcheye bhalo {skill} band. Ki kaj korse dekho.' },
      { id: 'best-bn-2', title: '{skill} e {band}, ekhon porjonto best', body: 'Ebar ki alada korle, dekhe nao 🚀' },
    ],
  },
  test: {
    en: [{ id: 'test-en-1', title: 'Notifications are on', body: "This is how we'll tell you when your band is ready." }],
    banglish: [{ id: 'test-bn-1', title: 'Notification chalu ache', body: 'Band ready hole ei bhabe janabo.' }],
  },
};

export const fillTemplate = (s: string, vars: Record<string, string>) => s.replace(/\{(\w+)\}/g, (m, k: string) => vars[k] ?? m);

/** A random template of `type` in `lang`, filled. ponytail: random until the round C bandit picks templates. */
export function renderPush(type: PushType, lang: PushLanguage, vars: Record<string, string>) {
  const list = PUSH_TEMPLATES[type][lang];
  const t = list[Math.floor(Math.random() * list.length)]!;
  return { templateId: t.id, title: fillTemplate(t.title, vars), body: fillTemplate(t.body, vars) };
}

// §5.6 guardrail list plus "plan" and a few more selling words, in English and Banglish (romanised Bangla). Whole words or phrases,
// case-insensitive. SHAME also covers §2 rule 5's comparison guilt ("your friends are ahead").
const SELLING = [
  'pro', 'premium', 'upgrade', 'upgrades', 'price', 'prices', 'pricing', 'buy', 'subscribe', 'subscription', 'website', 'link', 'plan', 'plans', 'unlock', 'discount', 'offer', 'offers', 'sale', 'purchase', 'pay', 'payment',
  'unlimited', 'trial', 'free trial', 'limited time', 'membership', 'plus', 'play store', 'coupon', 'promo',
  'kino', 'kinun', 'kinte', 'kineo', 'dam', 'daam', 'taka', 'tk', 'bkash', 'chhar', 'char dicchi', 'subscribe koro',
];
const SHAME = [
  'disappointed', 'disappointing', 'lazy', 'failing', 'ashamed', 'shame', 'pathetic', 'friends', 'everyone else', 'others are ahead', 'falling behind', 'left behind',
  'you missed', 'you broke', 'lost your streak', 'give up', 'giving up', 'no excuses', "why haven't you", "why didn't you",
  'olosh', 'alsho', 'lojja', 'lojjar', 'faki', 'fanki', 'pichiye porcho', 'pichiye pore', 'pichiye jaccho', 'bondhura', 'bondhu ra', 'shobai egiye',
  'bhule gecho', 'bhule gele', 'koroni', 'korli na', 'hariye',
];
const SYMBOLS = ['৳', '$', '%', 'nextband.app', 'http', 'www.'];
const GUILT_EMOJI = /[😢😞😔💔😭😟☹🙁]/u;
const words = (list: string[]) => new RegExp(`(?<![\\p{L}\\p{N}])(${list.join('|')})(?![\\p{L}\\p{N}])`, 'iu');
const SELLING_RE = words(SELLING);
const SHAME_RE = words(SHAME);
const EMOJI_RE = /\p{Extended_Pictographic}/gu;

/** Problems with a push's copy; empty = OK to send. Used by every send (server push.ts) and every template (tests). */
export function checkPush(m: { title: string; body: string }): string[] {
  const text = `${m.title}\n${m.body}`.replace(/[‘’]/g, "'");
  const out: string[] = [];
  if (!m.title.trim() || !m.body.trim()) out.push('empty');
  if ([...m.title].length > 40) out.push('title over 40 characters');
  if ([...m.body].length > 110) out.push('body over 110 characters');
  if ((text.match(EMOJI_RE) ?? []).length > 1) out.push('more than 1 emoji');
  if (/\{\w+\}/.test(text)) out.push('unfilled variable');
  const sell = text.match(SELLING_RE)?.[1] ?? SYMBOLS.find((s) => text.toLowerCase().includes(s));
  if (sell) out.push(`selling word: ${sell}`);
  const shame = text.match(SHAME_RE)?.[1] ?? text.match(GUILT_EMOJI)?.[0];
  if (shame) out.push(`shaming word: ${shame}`);
  return out;
}
