# Correcao da impressao Newland N960K - versao 1.5

## Causa raiz

A dependencia `com.github.lion0508:Newland_MESDK:4f0fc6d` nao continha o SDK.
O artefato produzido pelo JitPack era um JAR de 1.367 bytes somente com metadados,
enquanto o AAR real `MESDK-3.10.46-RELEASE.aar` possui 6.313.308 bytes.

O codigo tambem procurava nomes da API antiga (`com.newland.me.ConnUtils`,
`com.newland.mtype.ModuleType` e `COMMON_PRINTER`). No MESDK 3.10.46, as APIs
corretas sao `com.newland.sdk.me.ConnUtils`, `com.newland.sdk.mtype.ModuleType`
e os modulos `PRINTER_PRO`/`PRINTER`.

## Correcao aplicada

- O AAR real foi incluido localmente em `app/libs`, sem depender de uma publicacao
  JitPack incorreta.
- A conexao segue a ordem exigida pelo SDK: `init(Context)`, `connect()` e
  `getDevice()`.
- O app usa primeiro o modulo de script `PRINTER`, seguindo o exemplo publico
  funcional pesquisado, e conserva `PRINTER_PRO` como alternativa de firmware.
- Texto e bitmap aguardam o callback real `onSuccess`/`onError`; uma chamada `void`
  nao e mais tratada antecipadamente como impressao concluida.
- Estado sem papel, superaquecimento, baixa tensao e impressora ocupada sao
  registrados com diagnostico especifico.
- As rotas da Positivo L400 (`XchengAidlPrinter` e `BluetoothInternalPrinter`)
  nao foram alteradas.

## Origem e integridade do SDK

- Repositorio: <https://github.com/lion0508/Newland_MESDK>
- Commit: `4f0fc6d`
- Arquivo: `MESDK-3.10.46-RELEASE.aar`
- SHA-256: `84932BD9D69AB826FC36A3F66FAB9844FC9CA2DC9589A729CFF28036573F5B1F`

Como referencia de uso da impressora por script e callback, tambem foi analisado:
<https://github.com/mahdi-code007/PrinterSimple-Newland-Android>.

Fontes do fabricante consultadas:

- SDK Manager: <https://www.newlandnpt.com.br/cloud/sdk/>
- Familia SmartPOS N950K (Android e impressora termica de 58 mm):
  <https://www.newlandnpt.com.br/product/smartpos/156008.html>

## Validacao local

Executado com sucesso:

```bash
./gradlew :app:assembleDebug
./gradlew :app:compileReleaseKotlin :app:testDebugUnitTest
```

O APK de release assinado nao foi gerado porque o arquivo externo configurado
em `keystore.properties` (`C:\Users\Lucas\Documents\MinhaKeystore\visionid.jks`)
nao estava no ZIP nem na maquina de compilacao. Nao foi criada outra chave para
nao impedir a atualizacao sobre uma instalacao assinada anteriormente.

O teste fisico final deve ser feito no N960K com papel, observando o retorno
registrado na area de Logs do aplicativo.
