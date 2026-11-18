import type { Config } from "./config";
import { MercadoPagoError, type MercadoPagoClient } from "./mercadopago";
import type { Payment, PaymentsRepo } from "./payments-repo";

/** Valor fixo no servidor: nunca vem do cliente. */
export const AMOUNT_CENTS = 990;
export const EXPIRATION_MINUTES = 30;

export interface Deps {
  repo: PaymentsRepo;
  mp: MercadoPagoClient;
  config: Pick<Config, "PUBLIC_BASE_URL" | "MP_PAYER_EMAIL">;
  now?: () => Date;
  newKey?: () => string;
}

export interface Result {
  status: 200 | 201 | 400 | 500 | 502;
  body: Record<string, unknown>;
}

function view(p: Payment, qrCode: string, qrCodeBase64: string) {
  return {
    paymentId: p.mp_payment_id,
    qrCode,
    qrCodeBase64,
    expiresAt: p.expires_at,
    amountCents: p.amount_cents,
  };
}

function parseBody(body: unknown): { alarmId: string; deviceId: string } | null {
  if (typeof body !== "object" || body === null) return null;
  const { alarmId, deviceId } = body as Record<string, unknown>;
  if (typeof alarmId !== "string" || alarmId.trim() === "") return null;
  if (typeof deviceId !== "string" || deviceId.trim() === "") return null;
  return { alarmId: alarmId.trim(), deviceId: deviceId.trim() };
}

export async function createPayment(deps: Deps, rawBody: unknown): Promise<Result> {
  const input = parseBody(rawBody);
  if (!input) return { status: 400, body: { error: "invalid_body" } };

  const now = (deps.now ?? (() => new Date()))();
  const newKey = deps.newKey ?? (() => crypto.randomUUID());

  try {
    // Cobrança ainda válida para o mesmo alarme: devolve a existente, sem criar outra.
    const existing = await deps.repo.findActiveByAlarm(input.alarmId, now);
    if (existing?.mp_payment_id) {
      const charge = await deps.mp.getPix(existing.mp_payment_id);
      return { status: 200, body: view(existing, charge.qrCode, charge.qrCodeBase64) };
    }

    const expiresAt = new Date(now.getTime() + EXPIRATION_MINUTES * 60_000);
    const charge = await deps.mp.createPix({
      amount: AMOUNT_CENTS / 100,
      expiresAt,
      notificationUrl: `${deps.config.PUBLIC_BASE_URL.replace(/\/+$/, "")}/api/mp-webhook`,
      payerEmail: deps.config.MP_PAYER_EMAIL,
      description: "Desligar alarme",
      idempotencyKey: newKey(),
    });

    const saved = await deps.repo.create({
      mpPaymentId: charge.id,
      alarmId: input.alarmId,
      deviceId: input.deviceId,
      amountCents: AMOUNT_CENTS,
      expiresAt,
    });
    return { status: 201, body: view(saved, charge.qrCode, charge.qrCodeBase64) };
  } catch (e) {
    if (e instanceof MercadoPagoError) return { status: 502, body: { error: "payment_provider_error" } };
    return { status: 500, body: { error: "internal_error" } };
  }
}
