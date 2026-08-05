# VisionID 1.4.4 — correção de compilação do GeckoView

## Problema corrigido

O GeckoView 152 traz dependências AndroidX que exigem compilação contra a API 36 e uma versão mais recente do Android Gradle Plugin. Quando o Gradle Sync falhava, o Android Studio deixava de reconhecer `org.mozilla.geckoview`, causando erros como `Unresolved reference mozilla`, `GeckoSession`, `GeckoRuntime` e `GeckoView`.

## Alterações aplicadas

- `compileSdk`: 35 → 36
- Android Gradle Plugin: 8.7.3 → 8.11.1
- Gradle Wrapper: 8.9 → 8.13
- `androidx.core:core-ktx`: 1.15.0 → 1.18.0
- Dependência GeckoView mantida em `org.mozilla.geckoview:geckoview:152.0.20260713164047`
- Repositório Mozilla mantido em `https://maven.mozilla.org/maven2/`
- `targetSdk` mantido em 35

## Antes de compilar

Instale o Android SDK Platform 36 no SDK Manager e execute `Sync Project with Gradle Files`.
