export type PaymentStatus = "pending" | "approved" | "expired" | "used";

export interface Payment {
  id: string;
  mp_payment_id: string | null;
  alarm_id: string;
  device_id: string;
  amount_cents: number;
  status: PaymentStatus;
  expires_at: string;
  created_at: string;
  used_at: string | null;
}

export interface NewPayment {
  mpPaymentId: string;
  alarmId: string;
  deviceId: string;
  amountCents: number;
  expiresAt: Date;
}

export type PaymentInsert = Omit<Payment, "id" | "created_at" | "used_at">;

/** Acesso mínimo à tabela; a implementação real usa o Supabase, os testes usam um banco falso. */
export interface PaymentsStore {
  insert(row: PaymentInsert): Promise<Payment>;
  findByMpId(mpPaymentId: string): Promise<Payment | null>;
  /** Cobrança `pending` com expires_at > nowIso para o alarme, se houver. */
  findPendingByAlarm(alarmId: string, nowIso: string): Promise<Payment | null>;
  /** UPDATE ... WHERE mp_payment_id = ? AND status = ? (uma única operação); devolve as linhas alteradas. */
  updateWhere(
    match: { mpPaymentId: string; status: PaymentStatus },
    patch: Partial<Pick<Payment, "status" | "used_at">>,
  ): Promise<Payment[]>;
}

export function createPaymentsRepo(store: PaymentsStore) {
  return {
    create(input: NewPayment): Promise<Payment> {
      return store.insert({
        mp_payment_id: input.mpPaymentId,
        alarm_id: input.alarmId,
        device_id: input.deviceId,
        amount_cents: input.amountCents,
        status: "pending",
        expires_at: input.expiresAt.toISOString(),
      });
    },

    getByMpId(mpPaymentId: string): Promise<Payment | null> {
      return store.findByMpId(mpPaymentId);
    },

    /** Cobrança pending ainda não expirada do alarme (para não duplicar). */
    findActiveByAlarm(alarmId: string, now: Date): Promise<Payment | null> {
      return store.findPendingByAlarm(alarmId, now.toISOString());
    },

    /** pending -> approved. Devolve true se alterou. */
    async markApproved(mpPaymentId: string): Promise<boolean> {
      const rows = await store.updateWhere({ mpPaymentId, status: "pending" }, { status: "approved" });
      return rows.length > 0;
    },

    /** Atômico: só vira `used` se estiver `approved`. Devolve true apenas para quem conseguiu. */
    async markUsedIfApproved(mpPaymentId: string): Promise<boolean> {
      const rows = await store.updateWhere(
        { mpPaymentId, status: "approved" },
        { status: "used", used_at: new Date().toISOString() },
      );
      return rows.length > 0;
    },
  };
}

export type PaymentsRepo = ReturnType<typeof createPaymentsRepo>;
