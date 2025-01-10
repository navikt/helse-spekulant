package no.nav.helse.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asYearMonth
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry

internal class SammenligningsgrunnlagRiver(
    rapidsConnection: RapidsConnection,
    private val messageHandler: MessageHandler,
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireAll("@behov", listOf("InntekterForSammenligningsgrunnlag"))
            }
            validate {
                it.requireKey("@løsning", "fødselsnummer", "InntekterForSammenligningsgrunnlag.skjæringstidspunkt", "utkastTilVedtak")
                it.requireValue("@final", true)
                it.requireArray("@løsning.InntekterForSammenligningsgrunnlag") {
                    require("årMåned", JsonNode::asYearMonth)
                    requireArray("inntektsliste") {
                        requireKey("beløp")
                        requireAny("inntektstype", listOf("LOENNSINNTEKT", "NAERINGSINNTEKT", "PENSJON_ELLER_TRYGD", "YTELSE_FRA_OFFENTLIGE"))
                        interestedIn("orgnummer", "fødselsnummer", "fordel", "beskrivelse")
                    }
                }
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage, context: MessageContext, metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        messageHandler.håndter(SammenligningsgrunnlagMessage(packet))
    }
}
