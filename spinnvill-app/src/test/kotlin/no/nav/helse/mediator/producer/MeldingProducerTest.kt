package no.nav.helse.mediator.producer

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.Arbeidsgiverreferanse
import no.nav.helse.Fødselsnummer
import no.nav.helse.helpers.januar
import no.nav.helse.somArbeidsgiverref
import no.nav.helse.somFnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals

class MeldingProducerTest {
    private val fødselsnummer = Fødselsnummer("12345678910")
    private val organisasjonsnummer = Arbeidsgiverreferanse("987654321")
    private val vedtaksperiodeId = UUID.randomUUID()
    private val skjæringstidspunkt = 1.januar

    private val testRapid = TestRapid()
    private val meldingProducer = MeldingProducer(
        fødselsnummer = fødselsnummer,
        organisasjonsnummer = organisasjonsnummer,
        skjæringstidspunkt = skjæringstidspunkt,
        vedtaksperiodeId = vedtaksperiodeId,
        rapidsConnection = testRapid
    )

    @BeforeEach
    fun beforeEach() {
        testRapid.reset()
    }

    @Test
    fun `publiserer behov`() {
        meldingProducer.nyProducer(BehovProducer)
        meldingProducer.publiserMeldinger()
        val json = testRapid.inspektør.message(0)
        assertEquals("behov", json["@event_name"].asText())
        assertEquals(fødselsnummer, json["fødselsnummer"].asText().somFnr())
        assertEquals(organisasjonsnummer, json["organisasjonsnummer"].asText().somArbeidsgiverref())
        assertEquals(vedtaksperiodeId.toString(), json["vedtaksperiodeId"].asText())
        assertEquals(listOf("behov1", "behov2"), json["@behov"].map { it.asText() })
        assertEquals("behov", json["et"].asText())
    }

    @Test
    fun `publiserer hendelse`() {
        meldingProducer.nyProducer(HendelseProducer)
        meldingProducer.publiserMeldinger()
        val json = testRapid.inspektør.message(0)
        assertEquals("hendelse", json["@event_name"].asText())
        assertEquals(fødselsnummer, json["fødselsnummer"].asText().somFnr())
        assertEquals(organisasjonsnummer, json["organisasjonsnummer"].asText().somArbeidsgiverref())
        assertEquals(vedtaksperiodeId.toString(), json["vedtaksperiodeId"].asText())
        assertEquals("hendelse", json["en"].asText())
    }

    @Test
    fun `publiserer behov og hendelse`() {
        meldingProducer.nyProducer(HendelseProducer, BehovProducer)
        meldingProducer.publiserMeldinger()
        val hendelseJson = testRapid.inspektør.message(0)
        val behovJson = testRapid.inspektør.message(1)
        assertEquals("hendelse", hendelseJson["@event_name"].asText())
        assertEquals("behov", behovJson["@event_name"].asText())
        assertEquals("hendelse", hendelseJson["en"].asText())
        assertEquals("behov", behovJson["et"].asText())
    }

    private object BehovProducer: Producer {
        override fun ferdigstill(): List<Message> {
            return listOf(Message.Behov(setOf("behov1", "behov2"), mapOf("et" to "behov")))
        }
    }
    private object HendelseProducer: Producer {
        override fun ferdigstill(): List<Message> {
            return listOf(Message.Hendelse("hendelse", mapOf("en" to "hendelse")))
        }
    }
}
