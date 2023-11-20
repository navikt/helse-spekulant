package no.nav.helse.mediator

import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.db.Dao
import no.nav.helse.kafka.MessageHandler
import no.nav.helse.kafka.SammenligningsgrunnlagMessage
import no.nav.helse.kafka.UtkastTilVedtakMessage
import no.nav.helse.kafka.UtkastTilVedtakRiver
import no.nav.helse.modell.Sykefraværstilfelle
import no.nav.helse.modell.avviksvurdering.Beregningsgrunnlag
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.LoggerFactory
import java.time.LocalDate

class Mediator(private val rapidsConnection: RapidsConnection, private val dao: Dao) : MessageHandler {

    private companion object {
        private val logg = LoggerFactory.getLogger(this::class.java)
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        UtkastTilVedtakRiver(rapidsConnection, this)
    }

    override fun håndter(utkastTilVedtakMessage: UtkastTilVedtakMessage) {
        logg.info("Behandler utkast_til_vedtak for {}", kv("vedtaksperiodeId", utkastTilVedtakMessage.vedtaksperiodeId))
        sikkerlogg.info(
            "Behandler utkast_til_vedtak for {}, {}",
            kv("fødselsnummer", utkastTilVedtakMessage.fødselsnummer),
            kv("vedtaksperiodeId", utkastTilVedtakMessage.vedtaksperiodeId)
        )
        val behovProducer = BehovProducer(
            aktørId = utkastTilVedtakMessage.aktørId,
            fødselsnummer = utkastTilVedtakMessage.fødselsnummer,
            vedtaksperiodeId = utkastTilVedtakMessage.vedtaksperiodeId,
            organisasjonsnummer = utkastTilVedtakMessage.organisasjonsnummer,
            rapidsConnection = rapidsConnection
        )
        val beregningsgrunnlag = Beregningsgrunnlag(utkastTilVedtakMessage.beregningsgrunnlag)
        val sykefraværstilfelle = sykefraværstilfelle(
            fødselsnummer = utkastTilVedtakMessage.fødselsnummer,
            skjæringstidspunkt = utkastTilVedtakMessage.skjæringstidspunkt,
            behovProducer = behovProducer
        )
        sykefraværstilfelle.håndter(beregningsgrunnlag)
        behovProducer.finalize()
    }

    override fun håndter(sammenligningsgrunnlagMessage: SammenligningsgrunnlagMessage) {
        TODO("Not yet implemented")
    }

    private fun sykefraværstilfelle(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
        behovProducer: BehovProducer
    ): Sykefraværstilfelle {
        TODO()
//        val sykefraværstilfelle = dao.finnSykefraværstilfelle(fødselsnummer, skjæringstidspunkt) ?: Sykefraværstilfelle.nyttTilfelle()
//        avviksvurdering.register()
//        return Sykefraværstilfelle(skjæringstidspunkt).also {
//            it.register(behovProducer)
//        }
    }
}