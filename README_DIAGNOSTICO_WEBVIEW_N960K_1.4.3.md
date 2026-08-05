# VisionID 1.4.3 — correção e diagnóstico profundo do WebView no N960K

## O que esta versão muda

Esta versão não força mais `LAYER_TYPE_SOFTWARE` nem `LAYER_TYPE_HARDWARE` na jornada normal. O WebView usa `LAYER_TYPE_NONE`, que deixa o Android/Chromium escolher o caminho de composição.

A jornada WebView agora roda em um processo dedicado (`:webcontent`), separado de CameraX, ML Kit, impressão e SDKs nativos. O diagnóstico roda em outro processo limpo (`:webdebug`). Em Android 9 ou superior, cada processo recebe um diretório de dados próprio por meio de `WebView.setDataDirectorySuffix()`.

Também foram adicionados:

- campo para digitar qualquer URL;
- teste com `https://example.com/`;
- teste HTML local, sem internet, DNS, TLS ou servidor;
- teste automático do HTML local em AUTO, HARDWARE e SOFTWARE;
- recriação completa do WebView;
- limpeza de cache, cookies e WebStorage;
- teste HTTP/DNS fora do WebView;
- análise do DOM por JavaScript;
- análise dos pixels efetivamente desenhados na tela;
- logs de travamento ou congelamento do processo renderizador;
- logs do APK do provedor, splits, bibliotecas compartilhadas, ABI e bibliotecas nativas;
- inspeção USB por `chrome://inspect` na variante `diagnostic`;
- confiança em CA instalada pelo técnico e HTTP sem criptografia somente nas variantes `debug`/`diagnostic`. O release continua restrito.

## Como compilar

No Android Studio, selecione a variante **diagnostic** em **Build Variants** e gere o APK.

Pelo terminal:

```bash
./gradlew assembleDiagnostic
```

O APK será criado em:

```text
app/build/outputs/apk/diagnostic/
```

## Preparação do N960K

1. Desinstale a versão anterior do VisionID.
2. Reinicie o N960K. Esse passo é importante depois de trocar o provedor WebView, porque o Android recria processos e arquivos compartilhados do renderizador durante a atualização/reinicialização.
3. Instale o APK `diagnostic` 1.4.3.
4. Abra a área técnica e toque em **DIAGNÓSTICO DO WEBVIEW**. A tela branca da jornada também possui um botão **DEBUG** no canto superior direito.

## Sequência de teste

### 1. Teste sem internet

Toque em **HTML LOCAL**.

O resultado esperado é uma página azul/verde com o texto `WEBVIEW LOCAL OK` e um contador JavaScript aumentando.

Depois toque em **ANALISAR DOM** e **ANALISAR TELA**.

### 2. Teste automático das três camadas

Toque em **AUTO 3 MODOS** e aguarde. O app recria o WebView e testa o HTML local nos modos:

1. AUTO/NONE;
2. HARDWARE;
3. SOFTWARE.

Ao final, abra **LOGS** e compare os eventos `DEBUG_PIXEL_PROBE` de cada modo.

### 3. Teste de rede sem WebView

Deixe `https://example.com/` no campo e toque em **TESTE HTTP/DNS**.

Esse teste usa `HttpURLConnection`, portanto separa falha de rede/DNS/TLS de falha do renderizador WebView.

### 4. Teste de página externa

Toque em **EXAMPLE.COM**. Depois teste a URL real digitando-a no campo e tocando em **ABRIR URL**.

Use também:

- **ANALISAR DOM**;
- **ANALISAR TELA**;
- **RECRIAR WEBVIEW**;
- **LIMPAR DADOS**;
- **CAMADA AUTO**, **HARDWARE** e **SOFTWARE**;
- **NAVEGADOR EXTERNO**, para verificar se existe outro navegador funcional no terminal.

## Como interpretar

| Resultado | Diagnóstico mais provável |
|---|---|
| HTML LOCAL aparece | O renderizador funciona; investigar rede, TLS, proxy, URL ou código da página. |
| HTML LOCAL não aparece em nenhum modo, mas o DOM contém conteúdo | Falha de composição/renderização do firmware, GPU ou processo sandbox do WebView. |
| HTML LOCAL não aparece e surge `DEBUG_RENDER_PROCESS_GONE` | Processo renderizador falhando ao iniciar; verificar instalação do provedor, ABI, splits e firmware. |
| TESTE HTTP/DNS falha | Problema de DNS, rota, proxy, TLS ou certificado; não é renderização. |
| TESTE HTTP/DNS funciona, HTML LOCAL funciona e URL externa falha | Problema específico do site, certificado, redirecionamento ou política de conteúdo. |
| `nearWhitePercent` perto de 100%, DOM preenchido e `VISUAL_STATE_READY` | O Chromium processou a página, mas a superfície não foi composta corretamente. |
| `providerSplitSourceDirs`/bibliotecas incompatíveis ou ausentes após instalação manual do WebView 150 | O pacote WebView pode ter sido instalado incompleto ou com ABI errada. Reinstale o conjunto correto de APKs/splits aprovado para o firmware ou use uma atualização oficial da Newland. |

## Inspeção USB

Com o APK `diagnostic` instalado:

1. Ative **Depuração USB** no N960K.
2. Conecte-o ao computador.
3. Abra o Chrome no computador.
4. Acesse `chrome://inspect/#devices`.
5. Localize `com.example.laranjinhaqrwebview:webdebug` ou `:webcontent` e clique em **inspect**.
6. Verifique as abas **Console**, **Network**, **Elements** e **Rendering**.

## Comandos ADB úteis

```bash
adb devices -l
adb shell dumpsys webviewupdate
adb shell pm path com.google.android.webview
adb shell dumpsys package com.google.android.webview
adb shell getprop ro.product.cpu.abilist
adb shell getprop ro.build.fingerprint
adb logcat -c
adb logcat -v time | grep -iE "chromium|webview|sandbox|renderer|gpu|linker|crash|fatal"
```

No Windows PowerShell, troque o último comando por:

```powershell
adb logcat -v time | Select-String -Pattern "chromium|webview|sandbox|renderer|gpu|linker|crash|fatal"
```

## Logs mais importantes para enviar

- `DEBUG_ENVIRONMENT`
- `DEBUG_LOCAL_HTML_LOAD`
- `DEBUG_PAGE_STARTED`
- `DEBUG_PAGE_COMMIT_VISIBLE`
- `DEBUG_PAGE_FINISHED`
- `DEBUG_VISUAL_STATE_READY`
- `DEBUG_DOM_PROBE`
- `DEBUG_PIXEL_PROBE`
- `DEBUG_NETWORK_OK` ou `DEBUG_NETWORK_FAILED`
- `DEBUG_RENDER_PROCESS_GONE`
- `DEBUG_RENDER_UNRESPONSIVE`
- `DEBUG_SSL_ERROR`

A combinação desses eventos diferencia, sem adivinhação, permissão, rede, TLS, DOM, GPU/composição e falha do pacote WebView.
