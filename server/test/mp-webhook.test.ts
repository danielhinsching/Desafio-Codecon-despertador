import { createHmac } from "node:crypto";
import { describe, expect, it } from "vitest";
import { handleMpWebhook, verifyMpSignature } from "../src/mp-webhook";
import { createPaymentsRepo, type Payment, type PaymentsStore } from "../src/payments-repo";

const DUMMY_SECRET = "test_webhook_secret_key_12345";
const DUMMY_TOKEN = "test_mp_access_token_dummy";

function fakeStore(initial: Payment[] = []): { store: PaymentsStore; rows: Payment[] } {
  const rows: Payment[] = [...initial];
  let seq = initial.length;
  const store: PaymentsStore = {
    async insert(row) {
      const p: Payment = { ...row, id: `id-${++seq}`, created_at: new Date().toISOString(), used_at: null };
      rows.push(p);
      return { ...p };
    },
    async findByMpId(id) {
      const r = rows.find((x) => x.mp_payment_id === id);
      return r ? { ...r } : null;
    },
    async findPendingByAlarm(alarmId, nowIso) {
      const r = rows.find((x) => x.alarm_id === alarmId && x.status === "pending" && x.expires_at > nowIso);
      return r ? { ...r } : null;
    },
    async updateWhere(match, patch) {
      const hit = rows.filter((r) => r.mp_payment_id === match.mpPaymentId && r.status === match.status);
      hit.forEach((r) => Object.assign(r, patch));
      return hit.map((r) => ({ ...r }));
    },
  };
  return { store, rows };
}

function makeSignature(params: {
  secret?: string;
  dataId?: string;
  requestId?: string;
  ts?: string;
}): string {
  const secret = params.secret ?? DUMMY_SECRET;
  const ts = params.ts ?? "1700000000";
  let manifest = "";
  if (params.dataId) manifest += `id:${params.dataId};`;
  if (params.requestId) manifest += `request-id:${params.requestId};`;
  manifest += `ts:${ts};`;

  const hmac = createHmac("sha256", secret);
  hmac.update(manifest);
  const hash = hmac.digest("hex");
  return `ts=${ts},v1=${hash}`;
}

const basePendingPayment: Payment = {
  id: "id-1",
  mp_payment_id: "mp-12345",
  alarm_id: "alarm-abc",
  device_id: "device-xyz",
  amount_cents: 990,
  status: "pending",
  expires_at: new Date("2030-01-01T00:00:00Z").toISOString(),
  created_at: new Date("2026-01-01T00:00:00Z").toISOString(),
  used_at: null,
};

describe("POST /api/mp-webhook", () => {
  it("assinatura inválida devolve 401 e não altera nada no banco", async () => {
    const { store, rows } = fakeStore([{ ...basePendingPayment }]);
    const repo = createPaymentsRepo(store);

    const req = new Request("http://localhost/api/mp-webhook?data.id=mp-12345", {
      method: "POST",
      headers: {
        "x-signature": "ts=1700000000,v1=invalido",
        "x-request-id": "req-1",
      },
    });

    const res = await handleMpWebhook(req, {
      config: { MP_ACCESS_TOKEN: DUMMY_TOKEN, MP_WEBHOOK_SECRET: DUMMY_SECRET },
      repo,
      fetchFn: async () => {
        throw new Error("Não deve chamar fetch");
      },
    });

    expect(res.status).toBe(401);
    expect(rows[0].status).toBe("pending");
  });

  it("assinatura ausente devolve 401 e não altera nada", async () => {
    const { store, rows } = fakeStore([{ ...basePendingPayment }]);
    const repo = createPaymentsRepo(store);

    const req = new Request("http://localhost/api/mp-webhook?data.id=mp-12345", {
      method: "POST",
    });

    const res = await handleMpWebhook(req, {
      config: { MP_ACCESS_TOKEN: DUMMY_TOKEN, MP_WEBHOOK_SECRET: DUMMY_SECRET },
      repo,
      fetchFn: async () => {
        throw new Error("Não deve chamar fetch");
      },
    });

    expect(res.status).toBe(401);
    expect(rows[0].status).toBe("pending");
  });

  it("pagamento approved com valor 9,90 vira approved na tabela", async () => {
    const { store, rows } = fakeStore([{ ...basePendingPayment }]);
    const repo = createPaymentsRepo(store);

    const sig = makeSignature({
      dataId: "mp-12345",
      requestId: "req-100",
    });

    const req = new Request("http://localhost/api/mp-webhook?data.id=mp-12345", {
      method: "POST",
      headers: {
        "x-signature": sig,
        "x-request-id": "req-100",
      },
      body: JSON.stringify({ action: "payment.updated", data: { id: "mp-12345" } }),
    });

    let fetchCalled = false;
    const fakeFetch: typeof fetch = async (url, init) => {
      fetchCalled = true;
      expect(String(url)).toBe("https://api.mercadopago.com/v1/payments/mp-12345");
      expect((init?.headers as Record<string, string>)?.Authorization).toBe(`Bearer ${DUMMY_TOKEN}`);
      return new Response(
        JSON.stringify({
          id: "mp-12345",
          status: "approved",
          transaction_amount: 9.9,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    };

    const res = await handleMpWebhook(req, {
      config: { MP_ACCESS_TOKEN: DUMMY_TOKEN, MP_WEBHOOK_SECRET: DUMMY_SECRET },
      repo,
      fetchFn: fakeFetch,
    });

    expect(res.status).toBe(200);
    expect(fetchCalled).toBe(true);
    expect(rows[0].status).toBe("approved");
  });

  it("pagamento com valor diferente de 9,90 não é aprovado", async () => {
    const { store, rows } = fakeStore([{ ...basePendingPayment }]);
    const repo = createPaymentsRepo(store);

    const sig = makeSignature({ dataId: "mp-12345", requestId: "req-200" });
    const req = new Request("http://localhost/api/mp-webhook?data.id=mp-12345", {
      method: "POST",
      headers: { "x-signature": sig, "x-request-id": "req-200" },
    });

    const fakeFetch: typeof fetch = async () =>
      new Response(JSON.stringify({ status: "approved", transaction_amount: 10.0 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const res = await handleMpWebhook(req, {
      config: { MP_ACCESS_TOKEN: DUMMY_TOKEN, MP_WEBHOOK_SECRET: DUMMY_SECRET },
      repo,
      fetchFn: fakeFetch,
    });

    expect(res.status).toBe(200);
    expect(rows[0].status).toBe("pending");
  });

  it("pagamento com status diferente de approved não é aprovado", async () => {
    const { store, rows } = fakeStore([{ ...basePendingPayment }]);
    const repo = createPaymentsRepo(store);

    const sig = makeSignature({ dataId: "mp-12345", requestId: "req-300" });
    const req = new Request("http://localhost/api/mp-webhook?data.id=mp-12345", {
      method: "POST",
      headers: { "x-signature": sig, "x-request-id": "req-300" },
    });

    const fakeFetch: typeof fetch = async () =>
      new Response(JSON.stringify({ status: "in_process", transaction_amount: 9.9 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const res = await handleMpWebhook(req, {
      config: { MP_ACCESS_TOKEN: DUMMY_TOKEN, MP_WEBHOOK_SECRET: DUMMY_SECRET },
      repo,
      fetchFn: fakeFetch,
    });

    expect(res.status).toBe(200);
    expect(rows[0].status).toBe("pending");
  });

  it("id desconhecido no banco não é aprovado", async () => {
    const { store, rows } = fakeStore([{ ...basePendingPayment }]);
    const repo = createPaymentsRepo(store);

    const sig = makeSignature({ dataId: "mp-desconhecido", requestId: "req-400" });
    const req = new Request("http://localhost/api/mp-webhook?data.id=mp-desconhecido", {
      method: "POST",
      headers: { "x-signature": sig, "x-request-id": "req-400" },
    });

    const fakeFetch: typeof fetch = async () =>
      new Response(JSON.stringify({ status: "approved", transaction_amount: 9.9 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const res = await handleMpWebhook(req, {
      config: { MP_ACCESS_TOKEN: DUMMY_TOKEN, MP_WEBHOOK_SECRET: DUMMY_SECRET },
      repo,
      fetchFn: fakeFetch,
    });

    expect(res.status).toBe(200);
    expect(rows[0].status).toBe("pending");
  });

  it("o mesmo aviso repetido não causa efeito extra (idempotente)", async () => {
    const { store, rows } = fakeStore([{ ...basePendingPayment, status: "approved" }]);
    const repo = createPaymentsRepo(store);

    const sig = makeSignature({ dataId: "mp-12345", requestId: "req-500" });
    const req = new Request("http://localhost/api/mp-webhook?data.id=mp-12345", {
      method: "POST",
      headers: { "x-signature": sig, "x-request-id": "req-500" },
    });

    const fakeFetch: typeof fetch = async () =>
      new Response(JSON.stringify({ status: "approved", transaction_amount: 9.9 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const res = await handleMpWebhook(req, {
      config: { MP_ACCESS_TOKEN: DUMMY_TOKEN, MP_WEBHOOK_SECRET: DUMMY_SECRET },
      repo,
      fetchFn: fakeFetch,
    });

    expect(res.status).toBe(200);
    expect(rows[0].status).toBe("approved");
  });

  it("verifyMpSignature valida corretamente formato ts e v1", () => {
    const sig = makeSignature({ dataId: "123", requestId: "req-1", ts: "1700000000" });
    expect(
      verifyMpSignature({
        xSignature: sig,
        xRequestId: "req-1",
        dataId: "123",
        secret: DUMMY_SECRET,
      })
    ).toBe(true);

    expect(
      verifyMpSignature({
        xSignature: sig,
        xRequestId: "req-1",
        dataId: "123",
        secret: "outro_secret",
      })
    ).toBe(false);
  });
});
