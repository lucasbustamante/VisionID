# Correção do erro FileAnalysisException / source must not be null

## Causa

O projeto estava usando Kotlin Gradle Plugin 2.0.21 enquanto dependências recentes,
como AndroidX/GeckoView e suas dependências transitivas, contêm metadata produzida
por uma versão mais nova do Kotlin. O compilador K2 2.0.21 pode encerrar com erro
interno em `FirIncompatibleClassExpressionChecker`, apontando por engano para uma
função comum do `AppLog.kt`.

## Alterações

- Kotlin Gradle Plugin: 2.0.21 -> 2.4.10
- Kotlin BOM/stdlib fixados em 2.4.10
- Migração de `kotlinOptions.jvmTarget` para `compilerOptions` com JVM 17
- Mantidos AGP 8.11.1, Gradle 8.13, compileSdk 36 e minSdk 26

## Primeira abertura

1. Extraia o ZIP em uma pasta nova.
2. Feche qualquer projeto antigo do VisionID no Android Studio.
3. Abra a nova pasta.
4. Use **File > Sync Project with Gradle Files**.
5. Se o Android Studio ainda reutilizar o daemon antigo, execute no terminal:

```bat
gradlew.bat --stop
gradlew.bat clean --refresh-dependencies
```

Não é necessário alterar o `AppLog.kt`; o ponto indicado no erro era apenas o local
onde o compilador encontrou a classe com metadata incompatível.
