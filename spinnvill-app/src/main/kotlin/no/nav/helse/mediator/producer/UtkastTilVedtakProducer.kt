package no.nav.helse.mediator.producer

import no.nav.helse.Fødselsnummer
import no.nav.helse.avviksvurdering.Avviksvurdering
import no.nav.helse.avviksvurdering.Kilde
import no.nav.helse.avviksvurdering.Visitor
import no.nav.helse.kafka.UtkastTilVedtakMessage
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class UtkastTilVedtakProducer(
    private val utkastTilVedtakMessage: UtkastTilVedtakMessage
) : Producer {

    private val utkastTilVedtak = mutableListOf<UtkastTilVedtakMessage>()

    internal fun registrerUtkastForUtsending(avviksvurdering: Avviksvurdering) {
        avviksvurdering.accept(Sammensyer(utkastTilVedtakMessage))
        utkastTilVedtak.add(utkastTilVedtakMessage)
    }

    override fun ferdigstill(): List<Message> {
        return utkastTilVedtak.map {
            Message.Behov(
                setOf("Godkjenning"), it.finalize()
            )
        }
    }

    private class Sammensyer(private val utkastTilVedtakMessage: UtkastTilVedtakMessage) : Visitor {
        override fun visitAvviksvurdering(
            id: UUID,
            fødselsnummer: Fødselsnummer,
            skjæringstidspunkt: LocalDate,
            kilde: Kilde,
            opprettet: LocalDateTime
        ) {
            if (kilde == Kilde.INFOTRYGD) return
            utkastTilVedtakMessage.leggTilAvviksvurderingId(id)
        }
    }
}
