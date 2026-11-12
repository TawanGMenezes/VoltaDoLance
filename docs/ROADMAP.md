# Próximas versões — Volta do Lance

## Etapa 1 — Alfa local, sem pagamento
- [x] Projeto Gradle Android com build GitHub Actions e teste unitário de janela temporal
- [x] Telas originais de câmera, ajustes e galeria
- [x] Captura segmentada e exportação MP4, voz experimental
- [x] Sem permissão de internet e sem serviços remotos
- [ ] Compilação bem-sucedida em ambiente com Android SDK instalado
- [ ] Testes práticos em OPPO Reno14 F
- [ ] Validar integridade, áudio, orientação e sem perdas entre fragmentos

## Etapa 2 — Beta confiável
- [ ] Configuração da duração pós-marcar (ex.: 3, 5, 10, 20, 30, 60 minutos e personalizado), com espaço livre mínimo e encerramento seguro; na alfa permanece fixada em 3 minutos
- [ ] Substituir reinicialização da MediaRecorder a cada 4 s por captura/encoder contínuos (Camera2 + MediaCodec + MediaMuxer), com buffer circular robusto
- [ ] Testar reconhecimento de palavra-chave offline independente do gravador sem disputa do microfone
- [ ] Salvar eventos com recuperação caso o app seja interrompido
- [ ] Diagnóstico local sem coletar dados pessoais, proteção contra espaço insuficiente
- [ ] Compatibilidade câmera frontal/traseira, lente e taxas de frames reportadas por aparelho
- [ ] Testes de acessibilidade, operação com mão molhada, modo alto contraste e botões grandes
- [ ] Revisão de memória, aquecimento, bateria e armazenamento

## Etapa 3 — Comercial
- [ ] Definir nome/marca disponível, logo próprio, política de privacidade, termos de uso
- [ ] Assinaturas com Google Play Billing (assinatura, restauração, cancelamento, grace period)
- [ ] Backend mínimo para validar estados de assinatura (se necessário), sem upload de vídeos
- [ ] Entitlements confiáveis, sem decisões de pagamento apenas no cliente
- [ ] Testes de licenciamento e compra em ambiente de testes da Play Store
- [ ] AAB release assinado, proteção da chave de assinatura, política de dados e fichas de privacidade
- [ ] Teste fechado, observação de crash/ANR, avaliações e publicação gradual

**Princípio:** a gravação nunca deve depender da resposta do servidor de compras durante a captura. Degradação de conexão não pode descartar vídeo.
