package br.com.voltadolance;

import android.content.Context;

final class AlphaNotice {
    static final int VERSION=1;
    static final String TEXT="TERMOS DA VERSÃO DE TESTE\n\n"+
        "O LanceVivo está em versão alfa gratuita. Não há cobrança, cartão, assinatura real ou conversão automática. As telas de planos são demonstrações e podem ser reiniciadas para testar. Aceitar este aviso não autoriza pagamento.\n\n"+
        "COMO FUNCIONA\nO histórico mantém somente os últimos 15, 30 ou 60 segundos. Trechos antigos não marcados são descartados continuamente. Marcar guarda esses segundos e a continuação até encerrar. O limite após marcar é separado do histórico.\n\n"+
        "Mantenha o app aberto. Ao sair ou bloquear a tela, câmera e voz param; um lance marcado recebe tentativa de salvamento. Abrir configurações descarta o histórico não marcado. Sem limite fixo continua sujeito à bateria, calor e espaço.\n\n"+
        "PRIVACIDADE\nCâmera e um único microfone alimentam a gravação e os comandos de voz locais. Este aplicativo não envia imagem, áudio ou frases para servidores. Não há conta, anúncios ou análise de uso nesta alfa. Permissões de câmera e microfone são solicitadas separadamente pelo Android.\n\n"+
        "Preferências, frases e a versão/data deste aceite ficam no aparelho até limpar dados ou desinstalar. O histórico fica em arquivos temporários privados. Vídeos salvos ficam na galeria, na pasta VoltaDoLance, até você apagar; podem permanecer após desinstalar. A sincronização da sua galeria depende de outros serviços do celular.\n\n"+
        "CUIDADOS\nTeste as frases antes de usar. Vento, ruído, distância, interrupções do Android, bateria, aquecimento e pouco espaço podem impedir o registro. Não há garantia de recuperar vídeos após encerramento forçado. A câmera ativa consome bateria, inclusive com prévia oculta. Pause nos intervalos e faça cópias dos lances importantes.\n\n"+
        "Respeite as pessoas filmadas, direitos de imagem e regras do local. Não use o app de forma que comprometa sua segurança. Esta versão não é um serviço de emergência. Seus direitos legais permanecem preservados.\n\n"+
        "PLANOS FUTUROS\nValores exibidos são uma proposta para teste. Uma contratação real exigirá confirmação própria, informação de preço, renovação e cancelamento. Desinstalar não cancela uma futura assinatura real. Nenhuma assinatura existe neste APK.\n\n"+
        "Informações legais e atendimento do lançamento comercial ainda estão em preparação. Este aviso descreve somente o teste local e não substitui os termos definitivos do serviço pago. Você pode reler este conteúdo em Configurações > Privacidade e ajuda.";
    static boolean accepted(Context context) {return context.getSharedPreferences("local_settings",0).getInt("notice_accepted_version",0)==VERSION;}
    static void accept(Context context) {context.getSharedPreferences("local_settings",0).edit()
            .putInt("notice_accepted_version",VERSION).putLong("notice_accepted_at",System.currentTimeMillis()).apply();}
}
