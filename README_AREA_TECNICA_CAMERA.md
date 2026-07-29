# VisionID V1.3 — Área técnica e teste de câmera

Alterações desta entrega:

- O toque prolongado de 3 segundos sobre a versão abre a **Área técnica**.
- A Área técnica possui os atalhos:
  - **Logs do aplicativo**;
  - **Teste de câmera**.
- O teste de câmera permite:
  - abrir a câmera traseira;
  - alternar entre câmera traseira e frontal quando ambas estiverem disponíveis;
  - tirar uma foto;
  - visualizar a foto capturada;
  - escolher se deseja imprimi-la na impressora térmica integrada.
- A fotografia é convertida para preto e branco e redimensionada para a bobina térmica.
- Impressão de imagem implementada para:
  - Positivo/XCheng L400 por `printBitmap` no serviço AIDL;
  - Newland N960K por `Printer.print(position, bitmap, timeout, TimeUnit)`;
  - fallback ESC/POS para `BluetoothPrinter` interno.
- A versão permanece **1.3** (`versionCode 4`).
