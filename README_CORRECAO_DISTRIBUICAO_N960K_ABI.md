# Correção de distribuição N960K — ABI

O log de distribuição da N960K mostrou `INSTALL_FAILED_NO_MATCHING_ABIS (-113)` ao tentar instalar o arquivo `app-arm64-v8a-release...apk`.

A versão 1.6 já limitava as bibliotecas nativas a ARM (`armeabi-v7a` e `arm64-v8a`), mas também habilitava `splits.abi`, produzindo APKs separados. Isso permitia que o pacote `arm64-v8a` fosse enviado isoladamente ao TOMS/MTMS.

## Correção aplicada

Foi removida apenas a geração de APKs separados por ABI. O `ndk.abiFilters` continua incluindo:

- `armeabi-v7a`
- `arm64-v8a`

A build `release` passa a produzir um único `app-release.apk`, contendo as duas arquiteturas ARM. Nenhuma Activity, fluxo de QR Code, GeckoView/WebView, câmera, impressão ou botão foi alterado.

## Distribuição

Gerar **Build > Generate Signed App Bundle or APK > APK > release** e distribuir o único arquivo:

`app/build/outputs/apk/release/app-release.apk`

Não distribuir um APK com `arm64-v8a` no nome para a N960K desse lote.
