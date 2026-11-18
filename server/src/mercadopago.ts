export interface PixCharge {
  id: string;
  qrCode: string;
  qrCodeBase64: string;
}

export interface CreatePixInput {
  amount: number;
  expiresAt: Date;
  notificationUrl: string;
  payerEmail: string;
  description: string;
  idempotencyKey: string;
}

export interface MercadoPagoClient {
  createPix(input: CreatePixInput): Promise<PixCharge>;
  getPix(id: string): Promise<PixCharge>;
}

/** Erro genérico: nunca carrega token nem corpo da resposta do Mercado Pago. */
export class MercadoPagoError extends Error {
  constructor(readonly httpStatus?: number) {
    super(`Falha ao falar com o Mercado Pago${httpStatus ? ` (HTTP ${httpStatus})` : ""}`);
    this.name = "MercadoPagoError";
  }
}

const BASE = "https://api.mercadopago.com/v1/payments";

/** Formato aceito pela API: yyyy-MM-ddTHH:mm:ss.SSS+00:00 */
export function formatMpDate(d: Date): string {
  return d.toISOString().replace("Z", "+00:00");
}

function parseCharge(json: unknown): PixCharge {
  const j = json as {
    id?: number | string;
    point_of_interaction?: { transaction_data?: { qr_code?: string; qr_code_base64?: string } };
  };
  const td = j?.point_of_interaction?.transaction_data;
  if (j?.id === undefined || !td?.qr_code || !td?.qr_code_base64) throw new MercadoPagoError();
  return { id: String(j.id), qrCode: td.qr_code, qrCodeBase64: td.qr_code_base64 };
}

export function createMercadoPagoClient(
  accessToken: string,
  fetchImpl: typeof fetch = fetch,
): MercadoPagoClient {
  const auth = { Authorization: `Bearer ${accessToken}` };

  async function call(url: string, init: RequestInit): Promise<unknown> {
    let res: Response;
    try {
      res = await fetchImpl(url, init);
    } catch {
      throw new MercadoPagoError();
    }
    if (!res.ok) throw new MercadoPagoError(res.status);
    try {
      return await res.json();
    } catch {
      throw new MercadoPagoError();
    }
  }

  return {
    async createPix(input) {
      const json = await call(BASE, {
        method: "POST",
        headers: { ...auth, "Content-Type": "application/json", "X-Idempotency-Key": input.idempotencyKey },
        body: JSON.stringify({
          transaction_amount: input.amount,
          description: input.description,
          payment_method_id: "pix",
          date_of_expiration: formatMpDate(input.expiresAt),
          notification_url: input.notificationUrl,
          payer: { email: input.payerEmail },
        }),
      });
      return parseCharge(json);
    },
    async getPix(id) {
      const json = await call(`${BASE}/${encodeURIComponent(id)}`, { method: "GET", headers: auth });
      return parseCharge(json);
    },
  };
}
