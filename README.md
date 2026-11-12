# Volta do Lance — Android alpha (código-fonte)

**Status:** versão de desenvolvimento **não compilada e não testada em celular** neste ambiente. Não há APK incluso neste pacote. Não use como único registro de uma pescaria importante.

Aplicativo Android independente, com nome, cores, organização de telas e textos próprios. As referências visuais compartilhadas pelo usuário serviram para identificar funcionalidades de categoria — não para reproduzir a identidade do aplicativo de referência.

## Funcionalidades desta alpha

- Câmera traseira, pré-visualização em retrato, áudio ambiente, vídeo 720p ou 1080p (30fps, dependente da câmera).
- Captura de fragmentos MP4 curtos e guarda uma janela **aproximada** de 15, 30 ou 60 segundos anteriores ao toque em **Guardar momento**. Pode haver pequenos intervalos entre fragmentos e imprecisão na duração anterior.
- Continua a captura após marcar o lance até **Encerrar e salvar**, limitada a três minutos por evento nesta alfa (a futura seleção de duração já consta no planejamento); depois une fragmentos e salva em `Movies/VoltaDoLance` pelo MediaStore.
- Galeria com reprodução dos vídeos; modo de prévia preta com a tela **acesa** (não é gravação garantida em segundo plano).
- Reconhecimento de voz **experimental e opcional**, usando mecanismo **no dispositivo** quando disponível: “Marcar momento”, “Guardar lance”, “Encerrar vídeo”, “Finalizar vídeo”, “Parar vídeo”. Em alguns dispositivos o microfone não pode ser compartilhado pela gravação e reconhecimento; use sempre o botão como fallback.
- Sem internet, analytics, contas, anúncios, bibliotecas de publicidade ou pagamentos. Solicita apenas CAMERA e RECORD_AUDIO.

## Limitações de segurança e funcionamento

**Somente com a atividade aberta.** Sair da tela, bloquear o celular ou trocar de aplicativo encerra a captura. Eventos em gravação ainda não finalizados podem ser perdidos. **O modo preto não bloqueia a tela.** Não há promessa de gravação com aparelho bloqueado. O Android pode interromper gravação por calor, falta de espaço, mudança de permissões ou disputa de câmera/microfone.

Os vídeos são armazenados no dispositivo. Arquivos temporários ficam dentro do armazenamento privado do app e são apagados após exportar ou sair da captura. Se o processo for encerrado inesperadamente, fragmentos podem permanecer no cache e serão limpos pelo Android no futuro; não há upload de arquivos.

**Teste com cenas sem valor** antes de levar para pescarias. Não existe mecanismo de restauração de vídeo após falhas do processo nesta alpha.

## Como gerar o APK em nuvem (GitHub Actions)

1. Crie um **repositório privado e vazio** no GitHub para este aplicativo.
2. Envie **todo o conteúdo desta pasta**, preservando `.github/workflows/android-debug.yml` e a pasta `app/`, na raiz do repositório. Você pode usar o GitHub Desktop ou o Git pelo computador.
3. No GitHub, abra **Actions** > **Build Android APK de teste** > **Run workflow**. Um push na branch `main` ou `master` também inicia o build.
4. Aguarde o resultado; confirme que os testes e a compilação passaram. Baixe o artefato **VoltaDoLance-debug-apk** na página da execução (ele vem como ZIP contendo `app-debug.apk`).
5. Instale apenas esse APK gerado **no seu próprio repositório** no OPPO Reno14 F; o Android pode pedir permissão para instalar apps desta fonte.
6. Conceda acesso à câmera e ao microfone somente enquanto usa o app. Teste primeiro 720p e 15 s, em ambiente seguro, conferindo se o MP4 aparece e reproduz corretamente na galeria.

**Importante:** o GitHub Action faz a compilação num servidor, mas precisa ser executado em um repositório com estes arquivos. Este ZIP não é um instalador. Builds `debug` usam uma chave de teste gerada na compilação: não servem para publicação na Google Play nem devem ser distribuídos comercialmente.

## Testes manuais essenciais no OPPO Reno14 F

- Permissões permitidas/negadas e recuperação depois de voltar às configurações.
- Prévia e áudio (720p → 1080p), orientação correta, imagem e sincronização A/V.
- Começar o buffer, aguardar 20–65 s; marcar; gravar mais 10 s; encerrar; conferir duração real e início do MP4.
- Repetir dez vezes para identificar perdas de segmentos, telas travadas ou falha de junção.
- Abrir galeria enquanto salva; sair da câmera; bloquear a tela; negar microfone; trocar de app; pouca memória/espaço.
- Voz ligada/desligada (inclusive **SEM rede**) e gravação de áudio simultânea.
- Aquecimento e consumo de bateria durante 10, 30 e 60 minutos com o dispositivo apoiado, sem cobrir saídas de calor.

## Estrutura para futura monetização

Esta alpha **não implementa plano pago**. Para publicar, primeiro estabilizar gravação e proteger os dados. O desenho de monetização previsto em `docs/ROADMAP.md` coloca recursos pagos atrás de um módulo futuro de **Google Play Billing**, com verificação de compra, restauração e gerenciamento de assinatura. Nunca habilitar cobrança antes de testar a oferta, fluxos de cancelamento, privacidade, estabilidade e conformidade com as políticas vigentes da Google Play.

## Licença e titularidade

Código entregue para avaliação e evolução pelo usuário. Não incorpora marca, imagens, textos ou código-fonte do aplicativo de referência. Antes da distribuição comercial, revisar o registro de marca, termos de uso, a política de privacidade e as licenças de qualquer dependência que venha a ser adicionada.
