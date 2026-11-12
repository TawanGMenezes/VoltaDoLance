# Volta do Lance — alpha 0.2.1

App Android independente, com nome, cores e textos próprios. Sem assinatura, cobrança, conta, anúncios ou envio de vídeos. Permissões: câmera e microfone; sem INTERNET.

## Novidades

- Indicador vermelho REC piscando, com cronômetro do tempo após marcar. Estados separados de preparação, histórico ativo, gravação, salvamento, vídeo salvo e erro.
- Botões de marcar/encerrar, galeria e ajustes acompanham o estado. Salvamento bloqueia novos comandos; falhas permitem retomar a câmera.
- Frases de marcar e encerrar editáveis nos ajustes, persistidas apenas no aparelho. Padrões: “Marcar momento” e “Encerrar vídeo”. Fale a frase inteira; acentos, caixa e pontuação são ignorados. Apenas a hipótese final mais provável é usada; resultados parciais não acionam comandos.
- Duração após marcar: 3, 10 (padrão), 20, 30, 60 minutos ou sem limite fixo. Parada com tentativa de salvamento por duração, pouco espaço, bateria em 5% fora do carregador ou estado térmico SEVERE do Android. A reserva inclui duas cópias do evento para montagem e publicação, mais margem e fragmentos futuros.
- Qualidades consultadas em Camera2: tamanhos de MediaRecorder e prévia, duração mínima dos frames, faixas AE e codificador AVC. 4K30, 1080p60 e outros modos aparecem quando informados como compatíveis. 4K60 só aparece com suporte informado para essa combinação; não é prometido. Sessões de alta velocidade especiais não são usadas. Uma rejeição tenta modos inferiores e limpa o histórico incompatível antes de marcar. A qualidade ativa aparece na tela.
- Galeria com miniaturas carregadas fora da interface e ícone de reprodução.

## Histórico e privacidade

O histórico anterior de 15, 30 ou 60 segundos continua em fragmentos privados de aproximadamente 4 segundos. Há pequenas lacunas entre fragmentos e a seleção anterior é aproximada. Ao encerrar, os fragmentos são unidos e o MP4 é publicado em `Movies/VoltaDoLance` usando MediaStore/IS_PENDING. Temporários são removidos após exportação ou descarte normal. Fragmentos de um processo interrompido podem permanecer no cache até sua limpeza pelo Android.

A captura funciona com esta tela aberta. Ao sair ou bloquear, um evento marcado é encerrado com tentativa de salvamento; o histórico sem marcação é descartado. O modo de prévia preta mantém o indicador visível e não bloqueia o aparelho. A exportação continua durante a pausa da atividade, mas encerramento forçado do processo pode perder o evento. Não há restauração automática de vídeos após falhas do processo.

Voz é experimental, opcional e exclusivamente no dispositivo (Android 12+ com mecanismo local disponível). Alguns aparelhos não compartilham o microfone entre reconhecimento e gravação. Falhas repetidas desativam a voz; os botões continuam disponíveis. Segurança de espaço/bateria/calor reduz riscos, sem garantir salvamento diante de falha de hardware ou desligamento.

## APK pelo GitHub Actions

Acesse [Actions](https://github.com/TawanGMenezes/VoltaDoLance/actions), escolha a execução mais recente bem-sucedida da branch `main`, e baixe **VoltaDoLance-debug-apk** em **Artifacts** (requer login no GitHub). Extraia `app-debug.apk` do ZIP e abra no celular.

O workflow executa todos os testes JVM/Robolectric, monta o APK debug, verifica assinatura e versão e testa instalação limpa, reinstalação e abertura em um emulador Android 15. Após aprovação na main, publica também o arquivo APK diretamente em Releases (pré-lançamento), para baixar sem extrair ZIP. Relatórios ficam no artefato **VoltaDoLance-test-results**. Ferramentas usadas: Java 17, Gradle 8.13, SDK/build-tools 36, AGP 8.12.2. Versão do app: `0.2.1-alpha`, código `3`, pacote `br.com.voltadolance.dev`.

A assinatura debug é de testes e o Actions conserva a chave de teste no cache entre builds enquanto esse cache estiver disponível. Se o Android recusar atualização por assinatura diferente, faça cópia dos vídeos antes de remover a versão antiga. Não use este build para distribuição comercial.

## Validação em aparelho ainda necessária

Os testes automatizados cobrem estados/controles/REC, correspondência de voz, persistência de frases, janela anterior e regras de segurança. Eles não substituem gravação no OPPO Reno14 F.

1. Primeiro grave em 720p, aguarde 20 segundos de histórico, marque e encerre por botão; reproduza o MP4 com áudio e orientação corretos.
2. Repita com frases personalizadas, verificando se há reconhecimento enquanto grava áudio.
3. Teste os modos informados (especialmente 4K30 e 1080p60), confirme qualidade e fluidez do arquivo e mensagens de fallback.
4. Confira REC, cronômetro, botões desabilitados no salvamento e miniatura na galeria. Repita dez eventos.
5. Marque e encerre imediatamente; depois teste saída da tela durante gravação e exportação. Confirme o vídeo salvo ao retornar.
6. Valide uma gravação longa com espaço disponível, encerramento manual no modo sem limite e estabilidade térmica. O limite configurado conta o tempo após marcar, além do histórico selecionado.
