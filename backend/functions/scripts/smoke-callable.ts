/**
 * Calls the deployed functions the way the app will: an anonymous Firebase Auth user plus an App Check token
 * exchanged from a registered debug token. Makes at most two real translations; the refusals it checks are
 * rejected before any provider is called, so they are free and not counted.
 *
 *   FIREBASE_API_KEY=… FIREBASE_APP_ID=1:367590759386:android:… APP_CHECK_DEBUG_TOKEN=… npm run smoke
 *
 * Needs the Anonymous sign-in provider enabled and the debug token registered for that app in App Check.
 */
const env = process.env;
const project = env.GOOGLE_CLOUD_PROJECT ?? "englishtibetandictionary";
const baseUrl = env.FUNCTIONS_BASE_URL ?? `https://asia-south1-${project}.cloudfunctions.net`;
const timeZone = "Asia/Thimphu";

function required(name: string): string {
  const value = env[name];
  if (!value) throw new Error(`Set ${name}`);
  return value;
}

async function postJson(url: string, body: unknown, headers: Record<string, string> = {}): Promise<unknown> {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  const json: unknown = await response.json();
  if (!response.ok && !(typeof json === "object" && json !== null && "error" in json)) {
    throw new Error(`${url}: HTTP ${response.status}`);
  }
  return json;
}

function field(value: unknown, ...path: string[]): unknown {
  let current = value;
  for (const key of path) {
    current =
      typeof current === "object" && current !== null ? (current as Record<string, unknown>)[key] : undefined;
  }
  return current;
}

const apiKey = required("FIREBASE_API_KEY");
const appId = required("FIREBASE_APP_ID");

const signUp = await postJson(`https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${apiKey}`, {
  returnSecureToken: true,
});
const signedIn = field(signUp, "idToken");
if (typeof signedIn !== "string") throw new Error(`anonymous sign-in failed: ${JSON.stringify(signUp)}`);
const idToken: string = signedIn;

const exchanged = await postJson(
  `https://firebaseappcheck.googleapis.com/v1/projects/${project}/apps/${appId}:exchangeDebugToken?key=${apiKey}`,
  { debugToken: required("APP_CHECK_DEBUG_TOKEN") },
);
const exchangedToken = field(exchanged, "token");
if (typeof exchangedToken !== "string") {
  throw new Error(`App Check exchange failed: ${JSON.stringify(exchanged)}`);
}
const appCheckToken: string = exchangedToken;

/** Calls a function with App Check, and with the user's ID token unless `signedIn` is false. */
async function call(name: string, data: unknown, options: { signedIn?: boolean } = {}): Promise<unknown> {
  const headers: Record<string, string> = { "X-Firebase-AppCheck": appCheckToken };
  if (options.signedIn ?? true) headers.Authorization = `Bearer ${idToken}`;
  const reply = await postJson(`${baseUrl}/${name}`, { data }, headers);
  console.log(`${name} ${JSON.stringify(data)}\n  → ${JSON.stringify(reply)}`);
  return reply;
}

let failures = 0;
function check(name: string, ok: boolean): void {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}`);
  if (!ok) failures++;
}

const status = await call("getTranslationStatus", { timeZone });
check("a new user has used nothing", field(status, "result", "used") === 0);

const tibetan = { text: "བཀྲ་ཤིས་བདེ་ལེགས།", source: "bo", target: "en", timeZone };
const first = await call("translate", tibetan);
check("Tibetan → English translates", typeof field(first, "result", "translation") === "string");
const usedAfterFirst = field(first, "result", "quota", "used");

const second = await call("translate", tibetan);
check("the repeat is served from cache", field(second, "result", "cached") === true);
check("the repeat is not counted", field(second, "result", "quota", "used") === usedAfterFirst);

const dzongkha = await call("translate", { text: "སྐུ་གཟུགས་བཟང་པོ།", source: "dz", target: "en", timeZone });
check("Dzongkha → English translates", typeof field(dzongkha, "result", "translation") === "string");

const wylie = await call("translate", { text: "bkra shis", source: "bo", target: "en", timeZone });
check("Wylie is refused", field(wylie, "error", "details", "reason") === "not_tibetan_script");

const usedBeforeRefusals = field(await call("getTranslationStatus", { timeZone }), "result", "used");

const empty = await call("translate", { text: " \u200B ", source: "bo", target: "en", timeZone });
check("whitespace only is refused", field(empty, "error", "details", "reason") === "empty_text");

const tooLong = await call("translate", { text: "ཀ".repeat(101), source: "bo", target: "en", timeZone });
check(
  "101 characters are refused",
  field(tooLong, "error", "details", "reason") === "text_too_long" &&
    field(tooLong, "error", "details", "length") === 101,
);

const pair = await call("translate", { text: "ཀ", source: "bo", target: "dz", timeZone });
check("Tibetan → Dzongkha is refused", field(pair, "error", "details", "reason") === "unsupported_pair");

const malformed = await call("translate", { text: 5, source: "bo", target: "en" });
check("a malformed request is refused", field(malformed, "error", "details", "reason") === "bad_request");

const signedOut = await call("translate", tibetan, { signedIn: false });
check(
  "a call without an ID token is refused",
  field(signedOut, "error", "status") === "UNAUTHENTICATED" &&
    field(signedOut, "error", "details", "reason") === "unauthenticated",
);

const usedAfterRefusals = field(await call("getTranslationStatus", { timeZone }), "result", "used");
check("refusals are not counted", usedAfterRefusals === usedBeforeRefusals);

process.exitCode = failures === 0 ? 0 : 1;
