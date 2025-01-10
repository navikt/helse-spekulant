package no.nav.helse

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

internal class SlettPersonRiver(
    rapidsConnection: RapidsConnection,
    private val dao: Dao
): River.PacketListener {

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "slett_person")
            }
            validate {
                it.requireKey("@id", "fødselsnummer")
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage, context: MessageContext, metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        sikkerlogg.info("Sletter person med fødselsnummer: $fødselsnummer")
        dao.slett(fødselsnummer.somFnr())
        context.publish(fødselsnummer, lagPersonSlettet(fødselsnummer))
    }

    @Language("JSON")
    private fun lagPersonSlettet(fødselsnummer: String) = """
        {
            "@event_name": "person_slettet",
            "fødselsnummer": "$fødselsnummer"
        }
    """.trimIndent()
}
