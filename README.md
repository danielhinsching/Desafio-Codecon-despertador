# Despertador

Um aplicativo de despertador para Android desenvolvido para quem tem sono pesado, cujo alarme só desliga após o cumprimento de um desafio físico em primeiro plano.

## Como Funciona

- **Regra do Alarme e Desafio:** Quando o alarme toca, ele calcula uma meta $N$ de sacudidas equivalente ao número de segundos decorridos desde 01/01/1970 (Unix epoch) naquele instante exato — um número na casa de **1,8 bilhão**, sem operação de módulo (`mod`).
- **Desligamento:** O alarme não possui botão simples de soneca ou desligamento; o usuário precisa sacudir o celular até zerar o contador regressivo.
- **Detecção:** O acelerômetro mede as oscilações acima de um limiar mínimo de aceleração (2,5 g), com intervalo mínimo entre repetições (0,3 s) para evitar fraudes ou batidas leves na mesa.
- **Primeiro Plano:** A contagem e a interação com o desafio só são processadas enquanto o aplicativo estiver aberto e em primeiro plano.
- **Volume:** O som do alarme sobe progressivamente em rampa linear (começando baixo e aumentando com o tempo) no áudio tocado pelo app.

## Estado Atual

| Funcionalidade / Componente | Status | Detalhes |
|---|---|---|
| **Cálculo da meta de sacudidas (`ChallengeTarget`)** | Pronto | Cálculo puro de segundos desde 1970 (~1,8 bilhão) em Kotlin (`Long`). |
| **Contador de sacudidas (`ShakeCounter`)** | Pronto | Lógica de contagem com intervalo mínimo e contagem regressiva até zero. |
| **Leitura do acelerômetro (`SensorSource`)** | Pronto | Captura real de eventos de aceleração via Android `SensorManager`. |
| **Rampa de volume (`VolumeRamp`)** | Pronto | Interpolação linear de volume em função do tempo decorrido. |
| **Persistência do desafio (`PendingChallengeStore`)** | Pronto | Armazenamento do desafio pendente em `SharedPreferences` para retomada ao reabrir o app. |
| **Tela do desafio (`ChallengeScreen` e `ChallengeViewModel`)** | Pronto | Interface em Jetpack Compose exibindo contador formatado com separador de milhar, botão de iniciar e feedback de conclusão. |
| **Verificador de escova de dentes (`ToothbrushVerifier` e detector)** | Pronto | Lógica de verificação e detecção local offline de escova de dente (`com.codecon.despertador.toothbrush`). |
| **Alarme exato na tela de bloqueio (Issue #17)** | Em andamento | Disparo de alarme com `AlarmManager` exato sobrepondo a tela bloqueada e tocando áudio em rampa. |
| **Desafio visual por foto da escova de dentes (Issues #45 a #49)** | Em andamento | Integração da câmera com a tela do desafio e fluxo de fallback para sacudidas caso a câmera falhe. |
| **Pagamento por Pix para desligar alarme (pasta `server/` e UI Pix)** | Cancelado | Ideia experimental de pagamento via Mercado Pago que foi descontinuada. O código existente está inativo e não integra o produto final. |
| **Versão iOS (`ios/`)** | Adiado | Estrutura inicial e regras de domínio iniciadas, mas o desenvolvimento foi pausado para foco exclusivo no Android. |

## Como Instalar o APK de Teste

Para testar o aplicativo diretamente em um aparelho Android físico ou emulador:

1. Acesse a aba **Actions** do repositório no GitHub.
2. Selecione o workflow **Android CI** na barra lateral esquerda.
3. Clique na execução mais recente concluída com sucesso (ícone verde de check) realizada no branch `main`.
4. Na parte inferior da página da execução, na seção **Artifacts**, clique em **app-debug** para baixar o arquivo ZIP.
5. Extraia o arquivo `app-debug.zip` em seu computador para obter o `app-debug.apk`.
6. Transfira o arquivo `app-debug.apk` para o celular (via cabo USB, Google Drive ou outro meio de transferência).
7. No dispositivo Android, abra o arquivo `.apk` e, quando solicitado pelo sistema operacional, confirme a permissão para **Instalar apps de fontes desconhecidas**.
8. Abra o app **Despertador** instalado e teste a tela do desafio.

## Como Rodar e Testar Localmente

O desenvolvimento do app Android utiliza Gradle com Kotlin DSL. Os comandos devem ser executados a partir da pasta `android/`:

### Pré-requisitos
- JDK 17 configurado (Java Development Kit).
- Android SDK instalado com API 35 (`compileSdk = 35`, `minSdk = 26`).

### Execução dos Testes Unitários
Para rodar a suíte completa de testes unitários locais (JUnit):
```bash
cd android
./gradlew testDebugUnitTest
```
*(No Windows, utilize `gradlew.bat testDebugUnitTest`)*

### Compilação do APK de Debug
Para compilar o projeto e gerar o executável APK de debug:
```bash
cd android
./gradlew assembleDebug
```
O arquivo gerado estará disponível em `android/app/build/outputs/apk/debug/app-debug.apk`.

## Estrutura de Pastas

```text
├── android/                   # Código-fonte completo do aplicativo Android
│   ├── app/                   # Módulo principal da aplicação (Kotlin + Jetpack Compose)
│   │   ├── src/main/java/     # Código de UI, Domínio, Sensores e Armazenamento
│   │   └── src/test/java/     # Testes unitários com JUnit
│   ├── gradle/                # Gradle wrapper
│   └── build.gradle.kts       # Configuração de build e dependências
├── server/                    # [CÓDIGO PARADO] Servidor serverless Vercel da funcionalidade cancelada de Pix
├── ios/                       # [ADIADO] Estrutura preliminar do projeto iOS
└── .github/
    └── workflows/             # Workflows de CI/CD automatizados do GitHub Actions
        ├── android.yml        # CI do Android: roda testes unitários e gera APK de debug
        ├── ios.yml            # CI do iOS (em runner macOS)
        └── server.yml         # CI do servidor Node.js/TypeScript
```

## Como o Time Trabalha

O projeto foi desenvolvido de forma colaborativa e ágil utilizando agentes autônomos de IA e desenvolvedores humanos seguindo estas regras de engenharia de software:

- **Kanban no GitHub Projects:** As tarefas são divididas em issues atômicas e acompanhadas pelas colunas `Todo` (Ready), `In progress`, `In review` e `Done`.
- **Branches e Pull Requests:** Cada tarefa é desenvolvida em uma branch temática específica (`back/<issue>-<slug>` ou `front/<issue>-<slug>`) derivada da `main`.
- **Rastreabilidade:** Cada Pull Request deve incluir no corpo a cláusula de fechamento automático (`Closes #<número>`).
- **Integração Contínua (CI):** Nenhum código entra na `main` sem que os workflows correspondentes no GitHub Actions passem com sucesso (compilação estrita e testes automatizados 100% verdes).
- **Code Review Obrigatório:** Antes de qualquer merge, o PR passa por revisão formal de código e critérios de aceite. Os merges na `main` são realizados via *Squash & Merge*.

## Limitações Conhecidas

- **App em primeiro plano:** A detecção do acelerômetro e a atualização do contador de sacudidas dependem de o app estar aberto e em primeiro plano; o contador pausa caso o app seja minimizado.
- **Alarme na tela de bloqueio:** O disparo do alarme por cima da tela de bloqueio com toque ininterrupto ainda está em fase de implementação (issue #17).
- **Sem suporte a iOS:** A versão para iOS foi descontinuada/adiada durante o hackathon; não há compatibilidade nem testes em aparelhos iPhone.
- **Funcionalidade Pix inativa:** A opção de pagamento por Pix exibida na interface é experimental e não possui suporte de produção nem processamento ativo.
