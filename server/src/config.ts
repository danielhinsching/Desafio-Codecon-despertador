export const REQUIRED_ENV = [
  "MP_ACCESS_TOKEN",
  "MP_WEBHOOK_SECRET",
  "MP_PAYER_EMAIL",
  "PUBLIC_BASE_URL",
  "SUPABASE_URL",
  "SUPABASE_SERVICE_ROLE_KEY",
] as const;

export type Config = Record<(typeof REQUIRED_ENV)[number], string>;

/** Lê e valida as variáveis de ambiente. A mensagem de erro lista só os NOMES ausentes, nunca valores. */
export function loadConfig(env: Record<string, string | undefined> = process.env): Config {
  const missing = REQUIRED_ENV.filter((name) => !env[name] || env[name]!.trim() === "");
  if (missing.length > 0) {
    throw new Error(`Variáveis de ambiente ausentes: ${missing.join(", ")}`);
  }
  const config = {} as Config;
  for (const name of REQUIRED_ENV) config[name] = env[name]!.trim();
  return config;
}
