# Impressão térmica — revisão 1.3

## Correção L400

A versão anterior aguardava obrigatoriamente `onComplete()` em `printerReset()`.
No firmware testado da Positivo L400, o comando era aceito, mas o callback não
era devolvido; por isso o aplicativo encerrava com “Tempo esgotado durante reset”.

Nesta versão:

- o serviço `com.xcheng.printerservice.PrinterService` é iniciado antes do bind;
- `printerInit()` e `printerReset()` são executados sem exigir callback;
- o texto é enviado em blocos para não sobrecarregar o buffer;
- somente um `onException()` real faz a operação falhar;
- a ausência de `onComplete()` não é mais tratada como erro.

## Integração N960K

Foi substituída a dependência genérica anterior pelo MESDK Newland 3.10.46,
disponibilizado no repositório público `lion0508/Newland_MESDK`, fixado no
commit `4f0fc6d`.

Fluxo usado:

1. `ConnUtils.getDeviceManager()`;
2. `DeviceManager.getDevice()`;
3. `getStandardModule(ModuleType.COMMON_PRINTER)`;
4. `printer.init()`;
5. `printer.print(texto, 30, TimeUnit.SECONDS)`.

A integração também procura versões do SDK existentes no firmware por
classloaders de pacotes Newland/Printer.

## Build

O projeto usa JitPack em `settings.gradle.kts`. Na primeira sincronização,
o Android Studio precisará de acesso à internet para baixar o MESDK.

Versão do app: 1.3 (`versionCode 4`).
