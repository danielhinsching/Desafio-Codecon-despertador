# Servidor Pix (Vercel)

Funções serverless em TypeScript (`api/`). Base para os endpoints do Pix (Mercado Pago + Supabase).

## Rodar local
```
cd server
npm install
cp .env.example .env.local   # preencha os valores (nunca commite)
npm test
npm run dev                  # vercel dev; GET /api/health -> {"ok":true}
```

## Variáveis de ambiente
Definidas só no ambiente do servidor (Vercel). O repositório é público: nenhum segredo no código.

`MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET`, `MP_PAYER_EMAIL`, `PUBLIC_BASE_URL`, `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`

`src/config.ts` valida todas e falha citando os nomes ausentes.
