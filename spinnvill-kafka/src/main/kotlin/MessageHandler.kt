interface MessageHandler {
    fun håndter(utkastTilVedtakMessage: UtkastTilVedtakMessage)

    fun håndter(sammenligningsgrunnlagMessage: SammenligningsgrunnlagMessage)

    fun håndter(avviksvurderingerFraSpleisMessage: AvviksvurderingerFraSpleisMessage)

    fun håndter(enAvviksvurderingFraSpleisMessage: EnAvviksvurderingFraSpleisMessage)
}