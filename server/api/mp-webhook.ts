import { handleMpWebhook } from "../src/mp-webhook";

export async function POST(req: Request): Promise<Response> {
  return handleMpWebhook(req);
}
