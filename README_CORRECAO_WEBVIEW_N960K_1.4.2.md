# VisionID 1.4.2 — correção do WebView no Newland N960K

## Diagnóstico confirmado pelos logs enviados

A versão 1.4.1 estava executando o WebView do N960K em camada **software**:

- `mode: software`
- `viewHardwareAcceleratedAtSetup: false`

Portanto, apesar de o `AndroidManifest.xml` permitir aceleração de hardware e o provedor WebView 150 estar ativo, o próprio código desligava a aceleração do componente no N960K.

O log `DOCUMENT_PROBE` com URL `about:` não representa a página testada. Ele era gerado durante o encerramento da Activity, porque `onDestroy()` carregava `about:blank` antes de destruir o WebView.

## Mudanças desta versão

1. N960/N960K usam explicitamente `View.LAYER_TYPE_HARDWARE`.
2. O modo software deixou de ser ativado automaticamente pelo modelo.
3. A Activity permanece com `android:hardwareAccelerated="true"`.
4. A página só é carregada depois que o WebView estiver anexado à janela.
5. O cache do WebView é limpo uma única vez após a atualização para a versão 1.4.2.
6. Foi removido o toque automático no centro da página, que podia disparar ações inesperadas.
7. O diagnóstico do DOM ignora `about:blank` e confirma a URL viva do WebView.
8. `onDestroy()` não carrega mais `about:blank`, evitando logs falsos.
9. Os logs agora registram `layerType`, aceleração da janela, dimensões e estado de anexação.

## Resultado esperado nos logs

No N960K:

- `mode: forced-hardware`
- `layerType: 2`
- `viewHardwareAcceleratedAtSetup: true`
- `windowHardwareAccelerated: true`
- `PAGE_LOAD_STARTED` com `attached: true`, largura e altura maiores que zero
- `DOCUMENT_PROBE` com a URL HTTPS real, e não `about:`

## Permissões

O projeto já declara:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.CAMERA`

`INTERNET` e `ACCESS_NETWORK_STATE` são permissões normais e não dependem de diálogo em tempo de execução. A câmera só interfere quando a página solicita captura de vídeo; ela não explica uma tela branca ao abrir a Wikipédia.

## Build

Abra o projeto no Android Studio, sincronize o Gradle e gere o APK. Remova a versão 1.4.1 do N960K antes de instalar a 1.4.2, ou limpe os dados do aplicativo durante o teste inicial.
