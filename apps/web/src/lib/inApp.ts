/**
 * Opened from the Play build of the Android app (its Custom Tab links add `?app=play`). That app is consumption-only, so nothing
 * reached from it may lead to a checkout (Google Play Payments policy): for the rest of this tab we hide Pricing/Upgrade and block
 * /pricing and /checkout. sessionStorage is per tab, so the user's normal browser tabs are unaffected.
 */
const KEY = 'nb-app';

export function inPlayApp(): boolean {
  try {
    if (new URLSearchParams(window.location.search).get('app') === 'play') sessionStorage.setItem(KEY, 'play');
    return sessionStorage.getItem(KEY) === 'play';
  } catch {
    return false;
  }
}
