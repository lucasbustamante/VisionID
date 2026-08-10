# VisionID 1.5 - N960K sem MANAGE_NEWLAND

## Objetivo

Manter a impressao termica da Newland N960K sem declarar a permissao
privilegiada `android.permission.MANAGE_NEWLAND` e sem alterar os adaptadores
que ja atendem a L400.

## Solucao aplicada

O MESDK 3.10.46 foi retirado porque o proprio AAR acrescentava
`MANAGE_NEWLAND` e outras permissoes de administracao ao manifesto final.
Simplesmente remover a linha do manifesto do aplicativo deixaria essa
dependencia privilegiada ativa e poderia causar falha em tempo de execucao.

A integracao da N960K agora usa o modulo de impressora do Newland NSDK 2.8.0:

1. `NSDKModuleManagerImpl` inicializa a API com o contexto do aplicativo.
2. O modulo `ModuleType.PRINTER` e obtido como `Printer`.
3. Comprovantes sao renderizados em paginas monocromaticas de 384 pontos.
4. Texto e fotografias sao codificados em PNG e enviados por `printImage`.
5. Estado, falta de papel, temperatura, tensao e retorno assincrono sao tratados.

O adaptador NSDK so e inicializado quando o terminal e identificado como
Newland. As classes e a ordem preferencial da L400 (`XchengAidlPrinter` e
`BluetoothInternalPrinter`) nao foram alteradas.

## Permissoes

A auditoria do APK compilado confirmou que nao estao presentes:

- `android.permission.MANAGE_NEWLAND`
- `android.permission.MANAGE_NEWLANDUART3`
- `android.permission.MANAGE_ANALOG_SERIAL`
- `android.permission.WRITE_APN_SETTINGS`

O NSDK possui declaracoes de localizacao destinadas a modulos Bluetooth que
nao sao usados nesta integracao. Essas duas declaracoes sao removidas durante
o merge do manifesto. As permissoes Bluetooth ja existentes continuam porque
fazem parte do fallback funcional da L400.

## Dependencia fixada

- Artefato: `ng.nownow.newland.nsdk:NewlandNsdk:2.8.0`
- Arquivo local: `app/libs/NewlandNsdk-2.8.0.aar`
- SHA-256: `ABDCC2C72691CFB6A4DE68B0AC46DC8A87B4723BCE1CBA049C9150494CCB2B27`
- Catalogo: <https://central.sonatype.com/artifact/ng.nownow.newland.nsdk/NewlandNsdk>
- Portal SDK Newland: <https://www.newlandnpt.com.br/cloud/sdk/>

O AAR inclui bibliotecas nativas `armeabi-v7a` e `arm64-v8a`; ambas foram
confirmadas dentro do APK gerado.

## Versao e verificacoes

- `versionName`: `1.5`
- `versionCode`: `14`
- `minSdk`: `26`
- `targetSdk`: `35`
- `compileSdk`: `36`
- `assembleDebug`: aprovado
- `compileReleaseKotlin`: aprovado
- assinatura do APK de teste: Android Debug, esquema V2, valida

Comando de compilacao:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:compileReleaseKotlin
```

## Observacao de homologacao

A compilacao e a auditoria estatica passaram, mas a impressao deve ser
homologada em uma N960K fisica, especialmente em relacao ao firmware instalado.
O APK fornecido e assinado para debug. Para publicar ou atualizar uma instalacao
assinada para producao e necessario compilar com o `visionid.jks` original, que
nao acompanha este codigo-fonte.
