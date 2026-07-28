# Assinatura do VisionID 1.1

Este projeto foi configurado para gerar o APK de release já assinado.

## Versão
- versionName: 1.1
- versionCode: 2

## Keystore
- Arquivo: visionid-release.jks
- Alias: visionid
- Senha da keystore: VisionID@1.1#2026
- Senha da chave: VisionID@1.1#2026

## Gerar o APK assinado
No terminal, na raiz do projeto:

Windows CMD/PowerShell:

    gradlew.bat clean assembleRelease

Git Bash:

    ./gradlew clean assembleRelease

O APK será gerado em:

    app/build/outputs/apk/release/app-release.apk

O arquivo não deverá mais ser gerado com o sufixo `-unsigned`.

IMPORTANTE: preserve a keystore e as senhas. Atualizações futuras do mesmo aplicativo precisam usar exatamente a mesma chave.
