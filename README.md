# Launcher UniãoTV

Protótipo Android nativo para TV boxes, Android 9 ou superior. O APK é de teste: não distribuir para clientes antes da validação em hardware e configuração de uma assinatura de produção estável.

## Implementado

- Tela inicial navegável pelo controle remoto, TV e YouTube.
- Texto de propaganda e número de suporte configuráveis.
- QR Code local para Pix copia e cola completo ou link de pagamento. Uma chave Pix isolada não é um código Pix completo.
- Administração com senha individual criada no primeiro uso, hash PBKDF2 e limite de tentativas.
- Suspensão/liberação manual da assinatura. YouTube permanece disponível.
- Em Device Owner: modo quiosque com lista de apps permitidos, bloqueio de instalações pelo usuário (incluindo APK de pendrive), desinstalações, usuários adicionais, modo seguro e redefinição pelo menu.
- Saída administrativa para manutenção. Não impede reset físico ou reinstalação do firmware.

## Ainda não implementado

Painel web, comandos remotos autenticados, atualização remota de APK, campanhas de imagens e confirmação automática de Pix. Este APK não se comunica com servidor e não contém credenciais de serviço.

## Gerar o APK pelo GitHub

Abra Actions > Gerar APK de teste. Após concluir com sucesso, baixe o artefato `box-launcher-apk-teste` e extraia `app-debug.apk`.

O fluxo usa Java 17, Gradle 8.11.1 e Android Gradle Plugin 8.9.1. Para compilar localmente, instale Android SDK 35 e use `gradle assembleDebug lintDebug`. O fluxo instala o Gradle diretamente, sem wrapper binário.

A chave de debug do runner pode mudar entre compilações. Atualizações em produção precisam de keystore permanente, guardado fora do repositório. Não desinstale o controlador de uma box gerenciada para tentar atualizar.

## Testar antes de bloquear

1. Instale o APK em uma box de teste e selecione-o como tela inicial.
2. Crie a senha administrativa. Guarde-a: ainda não existe recuperação remota.
3. Informe o identificador real do seu app de TV e do YouTube instalado. O padrão do YouTube para TV é `com.google.android.youtube.tv`; outros aparelhos podem usar outro pacote.
4. Configure suporte, propaganda e conteúdo de pagamento.
5. Confira navegação pelo controle, abertura dos dois apps e leitura do QR Code pelo celular.

## Provisionamento de gerenciamento (apenas box de teste)

O launcher instalado como APK comum não ganha poderes de gerenciamento. A box precisa aceitar Device Owner. Em uma box preparada, sem contas e sem outro administrador, o provisionamento de desenvolvimento pode ser feito com Android Platform Tools:

```text
adb install app-debug.apk
adb shell dpm set-device-owner br.com.boxlauncher/.BoxAdminReceiver
```

O fabricante pode impedir isso; ter processador Allwinner não confirma compatibilidade. Não redefina uma box com dados para tentar o comando sem preparar um backup e autorizar essa operação.

Depois do provisionamento, abra Administração > Ativar restrições do aparelho. A depuração não é bloqueada neste protótipo para manter um caminho de recuperação de desenvolvimento. Isso deve ser tratado antes de produção.

Para manutenção: Administração > Desativar restrições para manutenção. A redefinição pode então ser acessada nas configurações do Android; não há uma senha adicionada ao menu nativo, e sim um acesso administrativo que libera esse menu.

## Validação em box real

- Reiniciar e confirmar retorno ao launcher e manutenção das políticas.
- Tentar abrir apps não permitidos via Home, Recentes, configurações e links dos apps.
- Tentar instalar APK via pendrive sem bloquear teclado/controle USB.
- Suspender TV e confirmar que o app é bloqueado pelo sistema quando gerenciado.
- Confirmar que YouTube, suporte e pagamento continuam disponíveis.
- Desativar políticas com senha e realizar manutenção.
- Testar sem internet: as configurações locais continuam disponíveis.

Não usar este protótipo como garantia de bloqueio até completar esses testes no firmware escolhido.

