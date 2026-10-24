/**
 * Makes one real translation per provider and direction (about 80 characters, billable) and prints which
 * providers handle which directions, with suggested `config/translation.routes`.
 *
 *   AZURE_TRANSLATOR_KEY=… AZURE_TRANSLATOR_REGION=… npm run check-providers
 *
 * Google uses Application Default Credentials (`gcloud auth application-default login`) and the project in
 * GOOGLE_CLOUD_PROJECT (default englishtibetandictionary).
 */
import { GoogleAuth } from "google-auth-library";

import { createAzureProvider } from "../src/providers/azure.js";
import { createGoogleProvider } from "../src/providers/google.js";
import { checkProviders, formatReport, suggestRoutes } from "./check-providers-lib.js";

const env = process.env;
const googleAuth = new GoogleAuth({ scopes: "https://www.googleapis.com/auth/cloud-translation" });
const googleProject = env.GOOGLE_CLOUD_PROJECT ?? "englishtibetandictionary";

const rows = await checkProviders({
  azure: createAzureProvider({
    key: env.AZURE_TRANSLATOR_KEY ?? "",
    region: env.AZURE_TRANSLATOR_REGION ?? "",
    endpoint: env.AZURE_TRANSLATOR_ENDPOINT ?? "https://api.cognitive.microsofttranslator.com",
  }),
  google: createGoogleProvider({
    projectId: googleProject,
    // Local user credentials have no quota project of their own; bill the call to this one.
    quotaProject: googleProject,
    getAccessToken: async () => {
      const token = await googleAuth.getAccessToken();
      if (!token) throw new Error("no Application Default Credentials");
      return token;
    },
  }),
});

console.log(formatReport(rows));
console.log("\nSuggested config/translation routes:");
console.log(JSON.stringify(suggestRoutes(rows), null, 2));
