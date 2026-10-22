/**
 * Calls the deployed functions the way the app will: an anonymous Firebase Auth user plus an App Check token
 * exchanged from a registered debug token. Makes at most two real translations.
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

async function call(name: string, data: unknown): Promise<unknown> {
  const reply = await postJson(
    `${baseUrl}/${name}`,
    { data },
    { Authorization: `Bearer ${idToken}`, "X-Firebase-AppCheck": appCheckToken },
  );
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

process.exitCode = failures === 0 ? 0 : 1;
