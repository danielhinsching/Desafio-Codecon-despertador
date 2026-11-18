import { readdirSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const dir = new URL("../supabase/migrations/", import.meta.url);
const sql = readdirSync(dir)
  .filter((f) => f.endsWith("_payments.sql"))
  .map((f) => readFileSync(new URL(f, dir), "utf8"))
  .join("\n");

describe("migration payments", () => {
  it("existe e cria a tabela com RLS ligado", () => {
    expect(sql).toMatch(/create table public\.payments/i);
    expect(sql).toMatch(/alter table public\.payments enable row level security/i);
  });
  it("não cria políticas", () => {
    expect(sql).not.toMatch(/create policy/i);
  });
  it("indexa alarm_id e tem mp_payment_id unique", () => {
    expect(sql).toMatch(/index .* on public\.payments \(alarm_id\)/i);
    expect(sql).toMatch(/mp_payment_id text unique/i);
  });
});
