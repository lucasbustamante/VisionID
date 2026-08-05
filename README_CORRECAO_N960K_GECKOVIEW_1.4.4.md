# VisionID 1.4.4 — correção definitiva alternativa para N960K

## Diagnóstico confirmado

O teste **HTML LOCAL** funciona no N960K, mas URLs HTTP/HTTPS permanecem em branco. Isso separa o problema de desenho/HTML do problema de rede do Android System WebView. As permissões `INTERNET` e `ACCESS_NETWORK_STATE` já eram suficientes e são permissões normais concedidas na instalação.

## Mudança principal

No N960/N960K, `WebViewActivity` encaminha automaticamente a URL para `GeckoBrowserActivity`. GeckoView é um motor web autocontido da Mozilla e leva no APK seu próprio renderizador, JavaScript, DNS, TLS e pilha HTTP. Portanto, não depende do Android System WebView 93/150 nem do caminho Chromium do firmware Newland.

No L400 e em outros modelos, o Android System WebView permanece como antes.

## Permissões incluídas

- INTERNET
- ACCESS_NETWORK_STATE
- ACCESS_WIFI_STATE
- CAMERA
- RECORD_AUDIO
- MODIFY_AUDIO_SETTINGS

Câmera e microfone são tratados também no `GeckoSession.PermissionDelegate`.

## Diagnóstico adicional

A tela Diagnóstico WebView agora possui:

- **ABRIR COM GECKO**: abre a URL digitada no motor independente;
- **WEBVIEW SISTEMA**: força o teste no Android System WebView, mesmo no N960K;
- **PROXY DIRETO**: ignora proxy/PAC do sistema apenas no processo do WebView;
- **PROXY SISTEMA**: restaura a configuração padrão;
- **PERMISSÕES**: registra o estado efetivo das permissões do APK instalado;
- os testes anteriores de HTML local, HTTP/DNS, DOM, pixels e camadas.

## Compilação

A primeira compilação precisa de internet para baixar:

- `androidx.webkit:webkit:1.16.0`
- `org.mozilla.geckoview:geckoview:152.0.20260713164047`

Compile preferencialmente a variante `diagnostic` para os testes e depois `release`.

## Teste

1. Desinstale a versão anterior.
2. Instale a 1.4.4.
3. Reinicie o N960K.
4. Leia o QR da jornada normalmente.
5. O N960K deve abrir diretamente pelo GeckoView.
6. Nos logs, confirme `GECKO / ENGINE_STARTED`, `PAGE_START` e `PAGE_STOP success=true`.

O APK fica maior porque inclui um navegador completo, mas esse é exatamente o objetivo: remover a dependência do WebView defeituoso do firmware.
