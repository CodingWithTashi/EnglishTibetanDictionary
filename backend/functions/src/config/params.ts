import { defineSecret, defineString } from "firebase-functions/params";

/** Azure Translator key in Secret Manager. `unset` (or empty) leaves Azure out of every route. */
export const AZURE_TRANSLATOR_KEY = defineSecret("AZURE_TRANSLATOR_KEY");

export const AZURE_TRANSLATOR_REGION = defineString("AZURE_TRANSLATOR_REGION", {
  default: "",
  description: "Region of the Azure Translator resource, e.g. centralindia; empty for a global resource.",
});

export const AZURE_TRANSLATOR_ENDPOINT = defineString("AZURE_TRANSLATOR_ENDPOINT", {
  default: "https://api.cognitive.microsofttranslator.com",
  description: "Azure Translator text endpoint.",
});
