# VisionID 1.4.5 — correção CameraX / Guava

## Problema corrigido

O projeto apresentava erros de compilação em `QrScannerActivity.kt`, começando por:

- `Cannot access class com.google.common.util.concurrent.ListenableFuture`
- `Unresolved reference addListener`
- inferência incorreta do resultado de `ProcessCameraProvider.getInstance()`
- erros em cascata em `unbindAll()` e `bindToLifecycle()`

## Causa

O GeckoView adiciona dependências de mídia. A combinação com versões anteriores do CameraX podia deixar a API `ListenableFuture` indisponível no classpath de compilação.

## Alterações

- CameraX atualizado de `1.4.1` para `1.6.1` em todos os módulos.
- Guava Android fixada em `33.5.0-android`.
- `ListenableFuture<ProcessCameraProvider>` declarado explicitamente.
- `ProcessCameraProvider` declarado explicitamente após `get()`.
- listener definido como `Runnable` explícito.
- versão do app atualizada para `1.4.5` (`versionCode 10`).

## Configuração

- compileSdk 36
- minSdk 26
- targetSdk 35
- AGP 8.11.1
- Gradle 8.13
- Kotlin 2.4.10
- CameraX 1.6.1
- Guava 33.5.0-android
