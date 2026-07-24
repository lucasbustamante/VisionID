# VisionID / Bio Facial

Aplicativo Android em Kotlin para leitura de QR Code com câmera traseira e abertura segura da URL em WebView.

## Identidade
- Nome exibido na lista de aplicativos: **Bio Facial**
- Marca interna e splash: **VisionID**
- Versão inicial: **V1.0.0**
- Paleta visual inspirada nos tons de laranja do Itaú
- Ícone dividido entre biometria facial e QR Code

## Fluxo
1. Splash screen VisionID.
2. Tela inicial com botão **INICIAR**.
3. Câmera traseira com moldura e instruções.
4. Leitura automática de URL HTTPS em QR Code.
5. Abertura da página em WebView, com permissão de câmera para biometria facial.

## Gerar APK final
No Git Bash, com `JAVA_HOME` configurado:

```bash
./gradlew assembleRelease
```

O APK será criado em:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

Para distribuição, o APK release deve ser assinado com uma chave própria.
