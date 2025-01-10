package no.nav.helse.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory

internal class GodkjenningsbehovRiver(rapidsConnection: RapidsConnection, private val messageHandler: MessageHandler) :
    River.PacketListener {
    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireAll("@behov", listOf("Godkjenning"))
                it.forbid("@løsning")
                it.forbid("behandletAvSpinnvill")
            }
            validate {
                it.requireKey("fødselsnummer", "organisasjonsnummer", "vedtaksperiodeId")
                it.requireKey("Godkjenning.vilkårsgrunnlagId", "Godkjenning.skjæringstidspunkt")
                it.requireArray("Godkjenning.omregnedeÅrsinntekter") {
                    requireKey("organisasjonsnummer", "beløp")
                }
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage, context: MessageContext, metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        sikkerlogg.info(
            "Leser godkjenningsbehov {}",
            kv("Fødselsnummer", packet["fødselsnummer"].asText())
        )
        messageHandler.håndter(GodkjenningsbehovMessage(packet))
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }
}
