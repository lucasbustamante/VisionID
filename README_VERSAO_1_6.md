# VisionID 1.6 — redução do pacote de release

## Versão

- `versionName`: `1.6`
- `versionCode`: `15`

## Otimizações aplicadas

- geração de APKs separados para `armeabi-v7a` e `arm64-v8a`;
- remoção das variantes `x86` e `x86_64`, usadas apenas por emuladores;
- compactação das bibliotecas nativas dentro do APK;
- minificação de bytecode com R8;
- remoção de recursos não usados;
- manutenção apenas dos recursos de idioma português e inglês.

O GeckoView foi mantido porque é o motor usado pela família N960/N960K. Sua
remoção deixaria o APK muito menor, mas restauraria o problema de páginas em
branco do WebView de sistema nesses equipamentos.

## APK correto para cada equipamento

- `app-armeabi-v7a-release.apk`: Android ARM de 32 bits;
- `app-arm64-v8a-release.apk`: Android ARM de 64 bits.

Em um terminal conectado por ADB, a arquitetura pode ser consultada com:

```powershell
adb shell getprop ro.product.cpu.abi
```

Não instale as duas variantes no mesmo equipamento. Se a resposta começar com
`arm64`, use `arm64-v8a`; se for `armeabi-v7a`, use a variante de 32 bits.

## Resultado validado

- APK original universal: 529,53 MiB;
- APK 1.6 `arm64-v8a`: 91,15 MiB (redução de 82,8%);
- APK 1.6 `armeabi-v7a`: 88,51 MiB (redução de 83,3%);
- build `assembleRelease`: aprovado;
- minificação R8 e verificação Lint Vital: aprovadas;
- assinatura de release: validada nos esquemas V2 e V3.

## Observação sobre armazenamento

A compactação reduz o arquivo usado para distribuição. Durante a instalação, o
Android extrai as bibliotecas nativas; portanto, a economia no armazenamento
interno instalado é menor do que a redução observada no tamanho do APK.
