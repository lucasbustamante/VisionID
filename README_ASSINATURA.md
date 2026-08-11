# Assinatura do VisionID 1.6

O release deve ser assinado com a mesma chave das versões já instaladas para
que o Android aceite a atualização.

## Configuração local

1. Copie `keystore.properties.example` para `keystore.properties`.
2. Preencha o caminho da chave, o alias e as senhas reais.
3. Mantenha `keystore.properties` e o arquivo `.jks` fora do controle de versão.

O arquivo `.gitignore` já protege esses arquivos. Credenciais de assinatura não
devem ser colocadas em README, commits ou pacotes de código-fonte.

## Gerar o release

No PowerShell, na raiz do projeto:

```powershell
.\gradlew.bat :app:assembleRelease
```

Os APKs são gerados em `app/build/outputs/apk/release/`:

- `app-armeabi-v7a-release.apk` para ARM de 32 bits;
- `app-arm64-v8a-release.apk` para ARM de 64 bits.

Preserve a keystore original em local seguro. Atualizações futuras precisam usar
exatamente a mesma chave de assinatura.
