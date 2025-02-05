package no.nav.helse.mediator.producer

import no.nav.helse.avviksvurdering.*
import no.nav.helse.kafka.GodkjenningsbehovMessage

internal class GodkjenningsbehovProducer(
    private val godkjenningsbehovMessage: GodkjenningsbehovMessage,
) : Producer {

    private val godkjenningsbehov = mutableListOf<GodkjenningsbehovMessage>()

    internal fun registrerGodkjenningsbehovForUtsending(avviksvurderingsgrunnlag: Avviksvurderingsgrunnlag) {
        godkjenningsbehovMessage.leggTilAvviksvurderingId(avviksvurderingsgrunnlag.id)
        godkjenningsbehov.add(godkjenningsbehovMessage)
    }

    override fun ferdigstill(): List<Message> =
        godkjenningsbehov.map { Message.Behov(setOf("Godkjenning"), it.utgående()) }
}
