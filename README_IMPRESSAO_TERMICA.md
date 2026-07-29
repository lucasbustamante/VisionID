# Impressão térmica — L400 e N960K

## Integrações incluídas

- **Positivo L400 / firmware XCheng**
  - Conexão direta pelo serviço AIDL `com.xcheng.printerservice.IPrinterService`.
  - Inicialização, reset, impressão de texto e avanço da bobina por callback.
  - O código tenta o `bindService()` mesmo quando o firmware oculta o serviço de `resolveService()`.
  - Fallback ESC/POS para o dispositivo virtual interno `BluetoothPrinter`.

- **Newland N960K**
  - Dependência `ng.nownow.newland.nsdk:NewlandNsdk:2.8.0` pelo Maven Central.
  - Fluxo MESDK compatível com `ConnUtils.getDeviceManager()`, `ModuleType.COMMON_PRINTER`, `printer.init()` e `printer.print(texto, timeout, TimeUnit.SECONDS)`.
  - Compatibilidade adicional por reflexão para variantes NSDK e managers presentes no firmware.

## Comportamento

- Nunca usa `PrintManager`; portanto não abre a tela de salvar PDF.
- A impressão roda fora da thread principal.
- O comprovante é formatado para bobina de 32 caracteres.
- Em falha, cada backend e os dados do firmware ficam registrados em **Logs do aplicativo**.

## Build

Na primeira sincronização, o Android Studio precisa acessar o Maven Central para baixar o AAR Newland.

```bash
./gradlew :app:clean :app:assembleRelease --rerun-tasks
```
