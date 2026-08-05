# Correção de compatibilidade do GeckoView

A dependência `org.mozilla.geckoview:geckoview:152.0.20260713164047` declara `minSdk 26`.

Por isso, o projeto foi ajustado de:

- `minSdk = 24`

para:

- `minSdk = 26`

Configuração final principal:

- `compileSdk = 36`
- `targetSdk = 35`
- `minSdk = 26`
- Android Gradle Plugin `8.11.1`
- Gradle `8.13`

Não foi utilizado `tools:overrideLibrary`, pois isso poderia causar falhas em execução em dispositivos com Android abaixo da API 26.
