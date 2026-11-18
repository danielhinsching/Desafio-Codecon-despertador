import { describe, expect, it } from "vitest";
import { GET } from "../api/health";

describe("GET /api/health", () => {
  it("responde 200 com {ok:true}", async () => {
    const res = GET();
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ ok: true });
  });
});
