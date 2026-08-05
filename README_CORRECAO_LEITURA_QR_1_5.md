# VisionID 1.5 — correção da fila de leitura de QR Code

## Ajustes

- Somente uma análise do ML Kit pode ficar em andamento por vez.
- Frames recebidos enquanto outra análise está ativa são descartados imediatamente.
- O mesmo QR inválido não cria novas mensagens nem uma fila de tentativas.
- Quando nenhum QR aparece na câmera, o aviso é removido após 450 ms.
- O cronômetro de remoção não é reiniciado a cada frame vazio.
- Um QR válido tem prioridade e é aberto imediatamente, mesmo após um QR inválido.
- A correção está na `QrScannerActivity`, utilizada nos terminais L400/L14 e N960/N960K.
- A chave da trava recebeu indicador visual `ATIVADA` / `DESATIVADA`, cores e card clicável.
