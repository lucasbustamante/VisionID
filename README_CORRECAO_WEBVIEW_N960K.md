# VisionID 1.4.1 — correção do WebView no N960K

## Diagnóstico

A versão do Android System WebView não é a única responsável pela tela branca. O N960K usa Android 12 e pode apresentar uma falha específica na composição gráfica do WebView, além de poder ter diferenças de certificado/proxy em relação ao L400.

## Alterações aplicadas

- Compatibilidade automática para aparelhos da família `N960`/`N960K`:
  - o conteúdo do WebView começa com camada de renderização por software;
  - a janela da Activity continua com aceleração de hardware habilitada;
  - a prioridade do processo de renderização é mantida como importante.
- Recuperação de falha do renderizador:
  - implementação de `onRenderProcessGone`;
  - a instância inválida é removida e destruída;
  - a jornada é reaberta uma única vez em modo de compatibilidade.
- Diagnóstico completo da tela branca:
  - pacote e versão do provedor WebView realmente ativo;
  - início, progresso, página visível e término do carregamento;
  - erro HTTP da página principal;
  - erro de certificado HTTPS, sem ignorar o certificado inválido;
  - teste do DOM após o carregamento para distinguir página carregada de falha de renderização;
  - fabricante, modelo, produto, placa, Android e provedor WebView nos logs.
- Forçada uma nova invalidação/desenho após o conteúdo ficar visível.
- Versão atualizada para `1.4.1` (`versionCode 6`).

## Como validar no N960K

1. Compile e instale a versão 1.4.1.
2. Abra primeiro uma URL HTTPS simples e depois a jornada de biometria.
3. Caso ainda apareça tela branca, abra a área técnica mantendo pressionado o número da versão por 3 segundos.
4. Procure os eventos abaixo:
   - `WEBVIEW_PROVIDER`: confirma qual versão está realmente ativa;
   - `RENDERING_MODE_CONFIGURED`: deve indicar `software` e `n960Compatibility=true`;
   - `PAGE_VISIBLE` e `DOCUMENT_PROBE`: indicam que a página foi carregada e o problema era visual;
   - `SSL_ERROR`: indica certificado/proxy do terminal, não versão do WebView;
   - `MAIN_FRAME_HTTP_ERROR` ou `PAGE_LOAD_ERROR`: indica falha de rede/servidor;
   - `RENDER_PROCESS_GONE`: indica queda do processo do WebView e tentativa automática de recuperação.

## Observação de segurança

O aplicativo não ignora erros de certificado. Se aparecer `SSL_ERROR`, a correção correta é configurar no app a CA corporativa exata, ou instalar a CA de forma administrada no terminal. Não foi incluído código que aceite qualquer certificado.

## Build neste ambiente

Os XMLs e a estrutura do código foram validados. O build completo não foi executado aqui porque o Gradle Wrapper precisaria baixar dependências externas e este ambiente não possui acesso direto aos repositórios Gradle/Android.
