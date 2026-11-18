import { loadConfig } from "../src/config";
import { createPayment } from "../src/create-payment";
import { createMercadoPagoClient } from "../src/mercadopago";
import { createPaymentsRepo } from "../src/payments-repo";
import { createSupabaseStore } from "../src/supabase-store";

export async function POST(request: Request): Promise<Response> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: "invalid_body" }, { status: 400 });
  }

  let config;
  try {
    config = loadConfig();
  } catch {
    return Response.json({ error: "server_misconfigured" }, { status: 500 });
  }

  const result = await createPayment(
    {
      repo: createPaymentsRepo(createSupabaseStore(config)),
      mp: createMercadoPagoClient(config.MP_ACCESS_TOKEN),
      config,
    },
    body,
  );
  return Response.json(result.body, { status: result.status });
}
