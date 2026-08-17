# VisionID / Bio Facial

Aplicativo Android em Kotlin para leitura de QR Code com câmera traseira e abertura segura da URL em WebView.

## Identidade
- Nome exibido na lista de aplicativos: **Bio Facial**
- Marca interna e splash: **VisionID**
- Versão atual: **V1.6**
- Paleta visual inspirada nos tons de laranja do Itaú
- Ícone dividido entre biometria facial e QR Code

## Fluxo
1. Splash screen VisionID.
2. Tela inicial com botão **INICIAR**.
3. Câmera traseira com moldura e instruções.
4. Leitura automática de URL HTTPS em QR Code.
5. Abertura da jornada em GeckoView no N960/N960K ou WebView nos demais equipamentos, com permissão de câmera para biometria facial.

## Gerar APK final
No Git Bash, com `JAVA_HOME` configurado:

```bash
./gradlew assembleRelease
```

O APK será criado em:

```text
app/build/outputs/apk/release/app-universal-release.apk
```

O build também gera APKs otimizados para `armeabi-v7a` e `arm64-v8a`.
Para uma única distribuição compatível com L400 e N960K, use o APK universal.
Os APKs de release são assinados com a chave configurada em `keystore.properties`.
