package no.nav.helse.kafka

interface MessageHandler {
    fun håndter(message: UtkastTilVedtakMessage)

    fun håndter(sammenligningsgrunnlagMessage: SammenligningsgrunnlagMessage)

    fun håndter(avviksvurderingerFraSpleisMessage: AvviksvurderingerFraSpleisMessage)

    fun håndter(enAvviksvurderingFraSpleisMessage: EnAvviksvurderingFraSpleisMessage)
}