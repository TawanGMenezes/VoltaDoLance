# Volta do Lance — alpha 0.3.0

Aplicativo Android com identidade e textos próprios. Sem assinatura, cobrança, contas, anúncios ou upload. Somente CAMERA e RECORD_AUDIO; sem permissão INTERNET.

## Correções desta versão

- Uma única sessão Camera2 e codificadores AVC/AAC contínuos. A câmera não para/reinicia a cada quatro segundos nem ao marcar o lance. O MP4 usa uma linha do tempo comum, preservando os tempos entre áudio e vídeo.
- Um único AudioRecord alimenta o vídeo e o reconhecedor offline de português (Vosk/modelo incluído no APK). A voz não disputa o microfone com outro serviço. A tela distingue carregando, aguardando áudio, ouvindo, frase ouvida e erro/repetição da tentativa.
- Frases de marcar e encerrar personalizáveis e locais; acentos e caixa são ignorados, palavras completas são reconhecidas mesmo dentro de uma frase. “Encerrar” também funciona como comando de parada. Apenas hipóteses concluídas acionam comandos, evitando resultados parciais que mudam de interpretação.
- Ao aceitar o início: REC vermelho piscando, cronômetro e um bip curto. Ao aceitar o fim: REC encerra, dois bips e estado de salvamento. Os sons usam o volume de mídia do aparelho e valem para voz e botões.
- Botão para pausar o histórico e desligar câmera/microfone nos intervalos. Retomar câmera volta a preparar o histórico.

## Histórico limitado: não acumula horas sem ação

O histórico mantém uma janela deslizante de 15, 30 ou 60 segundos, começando em um quadro-chave próximo ao limite. GOPs antigos são apagados continuamente. Abrir às 8h e marcar às 14h não deixa seis horas de vídeo no cache: ficam apenas os segundos mais recentes, mais a pequena margem de um quadro-chave. Bytes codificados ficam em arquivos privados com limite por espaço; não é vídeo bruto na RAM.

Ao marcar, a parte recente fica protegida e o arquivo temporário passa a crescer com o lance até encerrar. Depois é exportado em `Movies/VoltaDoLance` via MediaStore/IS_PENDING e os temporários são removidos. Histórico não marcado é descartado ao pausar/sair. Arquivos de uma sessão interrompida são limpos no próximo processo antes de uma nova captura.

O app reserva espaço para montagem e cópia final, com margem para novos frames. Pouco espaço, bateria em 5% fora do carregador ou aquecimento elevado pausam a captura com aviso; num lance marcado, haverá tentativa de salvamento dos dados íntegros. Durações após marcar: 3, 10, 20, 30, 60 minutos ou sem limite fixo, sempre sujeitas aos recursos disponíveis.

**Histórico ativo consome bateria:** câmera, codificador e microfone continuam trabalhando. Use Pausar histórico nos intervalos. Manter apenas alguns segundos em disco controla armazenamento, não elimina o gasto de energia. A captura funciona com a tela do app aberta; ocultar a prévia não bloqueia o aparelho. Encerramento forçado do processo/desligamento pode perder o lance; não há recuperação garantida.

## Qualidade e voz

Camera2 informa tamanhos/fps de MediaCodec e prévia, faixas AE, tempos de frame e suporte AVC. Modos como 4K30/1080p60 são oferecidos somente quando reportados; uma configuração rejeitada tenta modo inferior antes de marcar. Não se promete 4K60 nem taxa constante em qualquer luz/aparelho.

O reconhecedor é local e o modelo português tem tamanho fixo no APK/armazenamento privado; ele não cresce com horas de uso. Ruído, vento, distância, pronúncia e palavras fora do vocabulário ainda afetam a voz. Confira o estado “ouvindo” e a frase ouvida na tela; botões continuam disponíveis. O app não envia áudio para serviços externos.

## APK e validação

Use [Releases](https://github.com/TawanGMenezes/VoltaDoLance/releases) para baixar o `.apk` diretamente. O Actions da main publica uma pré-versão somente após testes JVM/UI, assinatura/alinhamento, instalação/reinstalação e teste Android com Camera2/AVC/AAC e fala sintética em português.

Versão `0.3.0-alpha`, código `4`, pacote `br.com.voltadolance.dev`. Java 17, Gradle 8.13, SDK36, AGP8.12.2. Assinatura debug preservada no cache enquanto disponível; não é distribuição comercial. Modelo português Vosk small pt0.3 e Vosk API: Apache2; JNA distribuído sob Apache2. Atribuições/licenças incluídas no APK.

Ainda é necessário testar no OPPO real: comandos com a própria voz e frases escolhidas; vento/ruído; bips e volume; orientação e áudio; fluidez nos modos oferecidos; eventos longos, pausas/retomadas e espaço disponível. Testes de emulador não garantem funcionamento em todos os aparelhos.
