import { describe, expect, it } from "vitest";
import { REQUIRED_ENV, loadConfig } from "../src/config";

const full = Object.fromEntries(REQUIRED_ENV.map((n) => [n, `valor-${n}`]));

describe("loadConfig", () => {
  it("devolve a config quando todas as variáveis existem", () => {
    expect(loadConfig(full).MP_PAYER_EMAIL).toBe("valor-MP_PAYER_EMAIL");
  });

  it("falha listando os nomes ausentes, sem vazar valores", () => {
    const { MP_ACCESS_TOKEN: _a, SUPABASE_URL: _b, ...rest } = full;
    expect(() => loadConfig(rest)).toThrowError(/MP_ACCESS_TOKEN, SUPABASE_URL/);
    try {
      loadConfig(rest);
    } catch (e) {
      expect((e as Error).message).not.toContain("valor-");
    }
  });

  it("trata valor vazio como ausente", () => {
    expect(() => loadConfig({ ...full, MP_WEBHOOK_SECRET: "  " })).toThrowError(/MP_WEBHOOK_SECRET/);
  });
});
