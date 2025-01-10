package no.nav.helse.mediator.producer

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.Arbeidsgiverreferanse
import no.nav.helse.Fødselsnummer
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*

interface Producer {
    fun ferdigstill(): List<Message>
}

sealed class Message(
    val innhold: Map<String, Any>
) {
    class Behov(
        val behov: Set<String>,
        innhold: Map<String, Any>
    ) : Message(innhold)

    class Hendelse(
        val navn: String,
        innhold: Map<String, Any>
    ) : Message(innhold)
}

class MeldingProducer(
    private val fødselsnummer: Fødselsnummer,
    private val organisasjonsnummer: Arbeidsgiverreferanse,
    private val skjæringstidspunkt: LocalDate,
    private val vedtaksperiodeId: UUID,
    private val rapidsConnection: RapidsConnection
) {
    private companion object {
        private val logg = LoggerFactory.getLogger(MeldingProducer::class.java)
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }

    private val producers = mutableListOf<Producer>()

    internal fun nyProducer(vararg producer: Producer) {
        producers.addAll(producer)
    }

    internal fun publiserMeldinger() {
        producers
            .flatMap {
                it.ferdigstill().map { message ->
                    when (message) {
                        is Message.Behov -> message to JsonMessage.newNeed(message.behov, message.innhold)
                        is Message.Hendelse -> message to JsonMessage.newMessage(message.navn, message.innhold)
                    }
                }
            }.onEach { (_, json) ->
                json["fødselsnummer"] = fødselsnummer.value
                json["organisasjonsnummer"] = organisasjonsnummer.value
                json["skjæringstidspunkt"] = skjæringstidspunkt
                json["vedtaksperiodeId"] = vedtaksperiodeId
            }.forEach { (message, jsonMessage) ->
                rapidsConnection.publish(fødselsnummer.value, jsonMessage.toJson())
                logg(message)
                sikkerlogg(message, jsonMessage.toJson())
            }
    }

    private fun sikkerlogg(message: Message, json: String) {
        withMDC(
            mapOf(
                "fødselsnummer" to fødselsnummer.value,
                "organisasjonsnummer" to organisasjonsnummer.value,
                "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                "vedtaksperiodeId" to vedtaksperiodeId.toString(),
            )
        ) {
            when (message) {
                is Message.Behov -> sikkerlogg.info("Etterspør {}. Behov: {}", kv("behov", message.behov), json)
                is Message.Hendelse -> sikkerlogg.info("Publiserer melding med {}. Innhold: {}", kv("@event_name", message.navn), json)
            }
        }
    }

    private fun logg(message: Message) {
        withMDC(
            mapOf(
                "vedtaksperiodeId" to vedtaksperiodeId.toString(),
                "skjæringstidspunkt" to skjæringstidspunkt.toString(),
            )
        ) {
            when (message) {
                is Message.Behov -> logg.info("Etterspør {}", kv("behov", message.behov))
                is Message.Hendelse -> logg.info("Publiserer melding med {}", kv("@event_name", message.navn))
            }
        }
    }
}
