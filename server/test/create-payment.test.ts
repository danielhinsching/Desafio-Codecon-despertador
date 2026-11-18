import { describe, expect, it } from "vitest";
import { createPayment, type Deps } from "../src/create-payment";
import {
  MercadoPagoError,
  createMercadoPagoClient,
  type CreatePixInput,
  type MercadoPagoClient,
} from "../src/mercadopago";
import { createPaymentsRepo, type Payment, type PaymentsStore } from "../src/payments-repo";

function fakeStore(): PaymentsStore {
  const rows: Payment[] = [];
  return {
    async insert(row) {
      const p: Payment = { ...row, id: `id-${rows.length + 1}`, created_at: new Date().toISOString(), used_at: null };
      rows.push(p);
      return { ...p };
    },
    async findByMpId(id) {
      return rows.find((r) => r.mp_payment_id === id) ?? null;
    },
    async findPendingByAlarm(alarmId, nowIso) {
      return rows.find((r) => r.alarm_id === alarmId && r.status === "pending" && r.expires_at > nowIso) ?? null;
    },
    async updateWhere() {
      return [];
    },
  };
}

function fakeMp(opts: { fail?: boolean } = {}) {
  const created: CreatePixInput[] = [];
  const client: MercadoPagoClient = {
    async createPix(input) {
      if (opts.fail) throw new MercadoPagoError(500);
      created.push(input);
      return { id: `mp-${created.length}`, qrCode: "00020126-copia-e-cola", qrCodeBase64: "iVBORw0KGgo=" };
    },
    async getPix(id) {
      return { id, qrCode: "00020126-copia-e-cola", qrCodeBase64: "iVBORw0KGgo=" };
    },
  };
  return { client, created };
}

const NOW = new Date("2030-01-01T12:00:00.000Z");
function deps(mp: MercadoPagoClient, store = fakeStore(), now = NOW): Deps {
  return {
    repo: createPaymentsRepo(store),
    mp,
    config: { PUBLIC_BASE_URL: "https://exemplo.test/", MP_PAYER_EMAIL: "pagador@exemplo.test" },
    now: () => now,
    newKey: () => "chave-1",
  };
}

describe("createPayment", () => {
  it("201 com os 5 campos e linha pending", async () => {
    const { client, created } = fakeMp();
    const d = deps(client);
    const r = await createPayment(d, { alarmId: "a1", deviceId: "d1" });
    expect(r.status).toBe(201);
    expect(r.body).toEqual({
      paymentId: "mp-1",
      qrCode: "00020126-copia-e-cola",
      qrCodeBase64: "iVBORw0KGgo=",
      expiresAt: "2030-01-01T12:30:00.000Z",
      amountCents: 990,
    });
    expect((await d.repo.getByMpId("mp-1"))?.status).toBe("pending");
    expect(created[0]).toMatchObject({
      amount: 9.9,
      notificationUrl: "https://exemplo.test/api/mp-webhook",
      payerEmail: "pagador@exemplo.test",
      idempotencyKey: "chave-1",
    });
  });

  it("ignora valor enviado pelo cliente", async () => {
    const { client, created } = fakeMp();
    const r = await createPayment(deps(client), { alarmId: "a1", deviceId: "d1", amount: 0.01, amountCents: 1 });
    expect(r.status).toBe(201);
    expect(created[0].amount).toBe(9.9);
  });

  it("400 para corpo inválido", async () => {
    const { client } = fakeMp();
    const bodies = [null, "x", {}, { alarmId: "a" }, { alarmId: "", deviceId: "d" }, { alarmId: 1, deviceId: "d" }];
    for (const body of bodies) {
      expect((await createPayment(deps(client), body)).status).toBe(400);
    }
  });

  it("segunda chamada para o mesmo alarme não cria outra cobrança", async () => {
    const { client, created } = fakeMp();
    const d = deps(client);
    await createPayment(d, { alarmId: "a1", deviceId: "d1" });
    const r = await createPayment(d, { alarmId: "a1", deviceId: "d1" });
    expect(r.status).toBe(200);
    expect(r.body.paymentId).toBe("mp-1");
    expect(created).toHaveLength(1);
  });

  it("cobrança expirada permite nova", async () => {
    const { client, created } = fakeMp();
    const store = fakeStore();
    await createPayment(deps(client, store), { alarmId: "a1", deviceId: "d1" });
    const later = new Date(NOW.getTime() + 31 * 60_000);
    const r = await createPayment(deps(client, store, later), { alarmId: "a1", deviceId: "d1" });
    expect(r.status).toBe(201);
    expect(created).toHaveLength(2);
  });

  it("erro do Mercado Pago vira 502 sem detalhes", async () => {
    const { client } = fakeMp({ fail: true });
    const r = await createPayment(deps(client), { alarmId: "a1", deviceId: "d1" });
    expect(r.status).toBe(502);
    expect(JSON.stringify(r.body)).not.toMatch(/token|Bearer|500/i);
  });
});

describe("createMercadoPagoClient", () => {
  it("envia o contrato esperado e não vaza token/corpo nos erros", async () => {
    let seen: { url: string; init: RequestInit } | undefined;
    const ok = (async (url: string, init: RequestInit) => {
      seen = { url, init };
      return new Response(
        JSON.stringify({ id: 123, point_of_interaction: { transaction_data: { qr_code: "q", qr_code_base64: "b" } } }),
        { status: 201 },
      );
    }) as unknown as typeof fetch;
    const c = createMercadoPagoClient("TOKEN-DE-TESTE", ok);
    const charge = await c.createPix({
      amount: 9.9,
      expiresAt: NOW,
      notificationUrl: "https://x.test/api/mp-webhook",
      payerEmail: "p@x.test",
      description: "d",
      idempotencyKey: "k",
    });
    expect(charge).toEqual({ id: "123", qrCode: "q", qrCodeBase64: "b" });
    expect(seen?.url).toBe("https://api.mercadopago.com/v1/payments");
    const headers = seen?.init.headers as Record<string, string>;
    expect(headers["X-Idempotency-Key"]).toBe("k");
    const sent = JSON.parse(seen?.init.body as string);
    expect(sent).toMatchObject({
      payment_method_id: "pix",
      transaction_amount: 9.9,
      date_of_expiration: "2030-01-01T12:00:00.000+00:00",
    });

    const bad = (async () => new Response("segredo-no-corpo TOKEN-DE-TESTE", { status: 401 })) as unknown as typeof fetch;
    const err = await createMercadoPagoClient("TOKEN-DE-TESTE", bad)
      .getPix("1")
      .catch((e: Error) => e);
    expect(err).toBeInstanceOf(MercadoPagoError);
    expect((err as Error).message).not.toMatch(/TOKEN|segredo/);
  });
});
