import { describe, expect, it } from "vitest";
import { createPaymentsRepo, type Payment, type PaymentsStore } from "../src/payments-repo";

/** Banco falso em memória: updateWhere checa e altera de forma síncrona, como o UPDATE condicional do Postgres. */
function fakeStore(): PaymentsStore {
  const rows: Payment[] = [];
  let seq = 0;
  return {
    async insert(row) {
      if (rows.some((r) => r.mp_payment_id === row.mp_payment_id)) throw new Error("unique");
      const p: Payment = { ...row, id: `id-${++seq}`, created_at: new Date().toISOString(), used_at: null };
      rows.push(p);
      return { ...p };
    },
    async findByMpId(id) {
      const r = rows.find((x) => x.mp_payment_id === id);
      return r ? { ...r } : null;
    },
    async updateWhere(match, patch) {
      const hit = rows.filter((r) => r.mp_payment_id === match.mpPaymentId && r.status === match.status);
      hit.forEach((r) => Object.assign(r, patch));
      return hit.map((r) => ({ ...r }));
    },
  };
}

const input = {
  mpPaymentId: "mp-1",
  alarmId: "alarm-1",
  deviceId: "dev-1",
  amountCents: 990,
  expiresAt: new Date("2030-01-01T00:30:00Z"),
};

describe("payments repo", () => {
  it("create grava pending e getByMpId lê", async () => {
    const repo = createPaymentsRepo(fakeStore());
    const p = await repo.create(input);
    expect(p.status).toBe("pending");
    expect(p.amount_cents).toBe(990);
    expect((await repo.getByMpId("mp-1"))?.alarm_id).toBe("alarm-1");
    expect(await repo.getByMpId("nao-existe")).toBeNull();
  });

  it("markApproved só altera pending", async () => {
    const repo = createPaymentsRepo(fakeStore());
    await repo.create(input);
    expect(await repo.markApproved("mp-1")).toBe(true);
    expect(await repo.markApproved("mp-1")).toBe(false);
    expect((await repo.getByMpId("mp-1"))?.status).toBe("approved");
  });

  it("markUsedIfApproved falha se não estiver approved", async () => {
    const repo = createPaymentsRepo(fakeStore());
    await repo.create(input);
    expect(await repo.markUsedIfApproved("mp-1")).toBe(false);
    expect((await repo.getByMpId("mp-1"))?.status).toBe("pending");
  });

  it("duas chamadas simultâneas de markUsedIfApproved: só uma tem sucesso", async () => {
    const repo = createPaymentsRepo(fakeStore());
    await repo.create(input);
    await repo.markApproved("mp-1");
    const results = await Promise.all([repo.markUsedIfApproved("mp-1"), repo.markUsedIfApproved("mp-1")]);
    expect(results.filter(Boolean)).toHaveLength(1);
    const p = await repo.getByMpId("mp-1");
    expect(p?.status).toBe("used");
    expect(p?.used_at).not.toBeNull();
  });
});
