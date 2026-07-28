# VisionID 1.2

## Alterações
- Versão atualizada para 1.2 (`versionCode 3`).
- O leitor e o WebView aceitam URLs HTTP e HTTPS válidas, sem restrição por domínio ou palavra no endereço.
- Acesso à área de logs ao manter pressionado o número da versão por 3 segundos e confirmar o popup.
- Logs persistentes, mais recentes primeiro e agrupados por hora.
- Tela de detalhes para cada registro.
- Registro de inicialização, telas, câmera, QR Code, permissões Android/WebView, navegação, carregamento, console web, erros e falhas não tratadas.
- Parâmetros de consulta e fragmentos são removidos das URLs gravadas para evitar guardar tokens ou dados sensíveis.

## Observação de build
O build não pôde ser concluído neste ambiente porque o Gradle Wrapper precisa baixar o Gradle 8.9 e o acesso externo está bloqueado. Os XMLs foram validados localmente.

- Os logs administrativos foram removidos. O histórico agora se concentra no escaneamento do QR Code, câmera, permissões relacionadas e funcionamento do WebView.
