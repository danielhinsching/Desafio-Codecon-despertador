import { createHmac, timingSafeEqual } from "node:crypto";
import { loadConfig, type Config } from "./config";
import { createPaymentsRepo, type PaymentsRepo } from "./payments-repo";
import { createSupabaseStore } from "./supabase-store";

export interface MpWebhookDeps {
  config?: Pick<Config, "MP_ACCESS_TOKEN" | "MP_WEBHOOK_SECRET">;
  repo?: PaymentsRepo;
  fetchFn?: typeof fetch;
}

/**
 * Valida a assinatura do webhook do Mercado Pago via HMAC-SHA256.
 * Formato do header x-signature: ts=...,v1=...
 * Manifesto: id:[data.id];request-id:[x-request-id];ts:[ts];
 */
export function verifyMpSignature(params: {
  xSignature: string | null;
  xRequestId: string | null;
  dataId: string | null;
  secret: string;
}): boolean {
  if (!params.xSignature || !params.secret) {
    return false;
  }

  const parts = params.xSignature.split(",");
  let ts: string | undefined;
  let v1: string | undefined;

  for (const part of parts) {
    const [key, ...rest] = part.trim().split("=");
    const value = rest.join("=");
    if (key === "ts") ts = value;
    else if (key === "v1") v1 = value;
  }

  if (!ts || !v1) {
    return false;
  }

  const check = (includeId: boolean): boolean => {
    let manifest = "";
    if (includeId && params.dataId) {
      manifest += `id:${params.dataId};`;
    }
    if (params.xRequestId) {
      manifest += `request-id:${params.xRequestId};`;
    }
    manifest += `ts:${ts};`;

    const hmac = createHmac("sha256", params.secret);
    hmac.update(manifest);
    const calculatedHash = hmac.digest("hex");

    try {
      const bufA = Buffer.from(calculatedHash, "hex");
      const bufB = Buffer.from(v1!, "hex");
      if (bufA.length !== bufB.length) return false;
      return timingSafeEqual(bufA, bufB);
    } catch {
      return false;
    }
  };

  return check(true) || check(false);
}

/**
 * Handler principal do webhook do Mercado Pago.
 * POST /api/mp-webhook
 */
export async function handleMpWebhook(req: Request, deps?: MpWebhookDeps): Promise<Response> {
  const config = deps?.config ?? loadConfig();
  const xSignature = req.headers.get("x-signature");
  const xRequestId = req.headers.get("x-request-id");

  const url = new URL(req.url, "http://localhost");
  const queryDataId = url.searchParams.get("data.id") || url.searchParams.get("id");

  let body: any = null;
  try {
    body = await req.json();
  } catch {
    // Corpo vazio ou não-JSON
  }

  const dataId = queryDataId || (body?.data?.id ? String(body.data.id) : null) || (body?.id ? String(body.id) : null);

  const isValid = verifyMpSignature({
    xSignature,
    xRequestId,
    dataId,
    secret: config.MP_WEBHOOK_SECRET,
  });

  if (!isValid) {
    return Response.json({ error: "Unauthorized" }, { status: 401 });
  }

  // Se não houver ID de pagamento a processar, responde 200 rápido
  if (!dataId) {
    return Response.json({ ok: true, ignored: true });
  }

  // Consulta o pagamento na API do Mercado Pago
  const fetchFn = deps?.fetchFn ?? fetch;
  const mpUrl = `https://api.mercadopago.com/v1/payments/${dataId}`;

  let mpRes: Response;
  try {
    mpRes = await fetchFn(mpUrl, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${config.MP_ACCESS_TOKEN}`,
      },
    });
  } catch {
    return Response.json({ ok: false, error: "Failed to fetch Mercado Pago" }, { status: 502 });
  }

  if (!mpRes.ok) {
    return Response.json({ ok: true, approved: false });
  }

  let mpData: any;
  try {
    mpData = await mpRes.json();
  } catch {
    return Response.json({ ok: true, approved: false });
  }

  const status = mpData?.status;
  const amount = Number(mpData?.transaction_amount);
  const isAmountValid = Math.round(amount * 100) === 990;

  if (status !== "approved" || !isAmountValid) {
    return Response.json({ ok: true, approved: false });
  }

  let repo: PaymentsRepo;
  if (deps?.repo) {
    repo = deps.repo;
  } else {
    const fullConfig = loadConfig();
    const store = createSupabaseStore(fullConfig);
    repo = createPaymentsRepo(store);
  }

  const payment = await repo.getByMpId(String(dataId));
  if (!payment) {
    return Response.json({ ok: true, approved: false });
  }

  await repo.markApproved(String(dataId));

  return Response.json({ ok: true, approved: true });
}
