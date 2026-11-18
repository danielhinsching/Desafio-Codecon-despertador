import { createClient } from "@supabase/supabase-js";
import type { Config } from "./config";
import type { Payment, PaymentsStore } from "./payments-repo";

/** Store real sobre o Supabase (service role). O UPDATE condicional é uma única instrução SQL, logo atômico. */
export function createSupabaseStore(
  config: Pick<Config, "SUPABASE_URL" | "SUPABASE_SERVICE_ROLE_KEY">,
): PaymentsStore {
  const db = createClient(config.SUPABASE_URL, config.SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false },
  });
  return {
    async insert(row) {
      const { data, error } = await db.from("payments").insert(row).select().single();
      if (error) throw new Error(`payments.insert: ${error.message}`);
      return data as Payment;
    },
    async findByMpId(mpPaymentId) {
      const { data, error } = await db.from("payments").select().eq("mp_payment_id", mpPaymentId).maybeSingle();
      if (error) throw new Error(`payments.findByMpId: ${error.message}`);
      return (data as Payment | null) ?? null;
    },
    async updateWhere(match, patch) {
      const { data, error } = await db
        .from("payments")
        .update(patch)
        .eq("mp_payment_id", match.mpPaymentId)
        .eq("status", match.status)
        .select();
      if (error) throw new Error(`payments.updateWhere: ${error.message}`);
      return (data ?? []) as Payment[];
    },
  };
}
