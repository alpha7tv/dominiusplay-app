# Dominius Play — aplicativo Android

Aplicativo de TV e celular: **Canais ao vivo, Filmes e Séries**, tudo em português, com a identidade do Dominius Play.

- Ativação por **código de 8 números** (gerado no painel `dominiusplay.top/admin/meu-app` ou enviado no e-mail do teste).
- Lista (servidor, usuário e senha) entregue pelo painel; o cliente não digita nada além do código.
- **Avisos e publicidade** cadastrados no painel aparecem na tela inicial, em janela ao abrir o app e na tela de ativação.
- Atualização: o app avisa quando há versão nova publicada no painel (pode ser obrigatória).

## Como compilar
A cada envio de código, o GitHub Actions compila o APK e publica em **Releases**.
O número da versão é o número da execução. No painel use o mesmo número e o nome `1.0.<número>`.

### Assinatura (recomendado antes de distribuir)
Sem chave configurada, o APK sai com assinatura provisória (sem possibilidade de atualizar por cima de outra compilação).
Para uma chave fixa, crie estes *secrets* no repositório: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Estrutura
- `app/src/main/kotlin/top/dominiusplay/app`: telas e serviços (ativação, início, listas, player).
- API do painel: `https://dominiusplay.top/api/app/*`.
