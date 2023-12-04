package no.nav.helse.mediator.producer

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.*
import no.nav.helse.avviksvurdering.ArbeidsgiverInntekt
import no.nav.helse.avviksvurdering.Beregningsgrunnlag
import no.nav.helse.avviksvurdering.Sammenligningsgrunnlag
import no.nav.helse.helpers.januar
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asYearMonth
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.YearMonth

class AvviksvurderingProducerTest {
    private val testRapid = TestRapid()
    private val fødselsnummer = Fødselsnummer("12345678910")
    private val aktørId = AktørId("1234567891011")
    private val skjæringstidspunkt = 1.januar
    private val avviksvurderingProducer =
        AvviksvurderingProducer(
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            skjæringstidspunkt = skjæringstidspunkt,
            rapidsConnection = testRapid
        )

    @Test
    fun `produser avviksvurdering for akseptebelt avvik`() {
        avviksvurderingProducer.avvikVurdert(
            avviksprosent = 24.9,
            harAkseptabeltAvvik = true,
            beregningsgrunnlag = Beregningsgrunnlag.INGEN,
            sammenligningsgrunnlag = Sammenligningsgrunnlag(emptyList()),
            maksimaltTillattAvvik = 25.0
        )
        avviksvurderingProducer.finalize()
        assertEquals(1, testRapid.inspektør.size)
    }

    @Test
    fun `produser avviksvurdering for uakseptebelt avvik`() {
        avviksvurderingProducer.avvikVurdert(
            avviksprosent = 25.0,
            harAkseptabeltAvvik = false,
            beregningsgrunnlag = Beregningsgrunnlag.INGEN,
            sammenligningsgrunnlag = Sammenligningsgrunnlag(emptyList()),
            maksimaltTillattAvvik = 25.0
        )
        avviksvurderingProducer.finalize()
        assertEquals(1, testRapid.inspektør.size)
    }

    @Test
    fun `avviksvurdering kø tømmes etter hver finalize`() {
        avviksvurderingProducer.avvikVurdert(
            avviksprosent = 24.9,
            harAkseptabeltAvvik = true,
            beregningsgrunnlag = Beregningsgrunnlag.INGEN,
            sammenligningsgrunnlag = Sammenligningsgrunnlag(emptyList()),
            maksimaltTillattAvvik = 25.0
        )
        avviksvurderingProducer.finalize()
        avviksvurderingProducer.finalize()
        assertEquals(1, testRapid.inspektør.size)
    }

    @Test
    fun `ikke send ut avviksvurderingsmelding før finalize blir kalt`() {
        avviksvurderingProducer.avvikVurdert(
            avviksprosent = 24.9,
            harAkseptabeltAvvik = true,
            beregningsgrunnlag = Beregningsgrunnlag.INGEN,
            sammenligningsgrunnlag = Sammenligningsgrunnlag(emptyList()),
            maksimaltTillattAvvik = 25.0
        )
        assertEquals(0, testRapid.inspektør.size)
        avviksvurderingProducer.finalize()
        assertEquals(1, testRapid.inspektør.size)
    }

    @Test
    fun `produserer riktig format på avviksvurderingmelding`() {
        avviksvurderingProducer.avvikVurdert(
            avviksprosent = 24.9,
            harAkseptabeltAvvik = true,
            beregningsgrunnlag = Beregningsgrunnlag.opprett(
                mapOf(
                    "987654321".somArbeidsgiverref() to OmregnetÅrsinntekt(
                        400000.0
                    )
                )
            ),
            sammenligningsgrunnlag = Sammenligningsgrunnlag(
                listOf(
                    ArbeidsgiverInntekt(
                        arbeidsgiverreferanse = "987654321".somArbeidsgiverref(),
                        inntekter = listOf(
                            ArbeidsgiverInntekt.MånedligInntekt(
                                inntekt = InntektPerMåned(30000.0),
                                måned = YearMonth.from(1.januar),
                                fordel = null,
                                beskrivelse = null,
                                inntektstype = ArbeidsgiverInntekt.Inntektstype.LØNNSINNTEKT
                            )
                        )
                    )
                )
            ),
            maksimaltTillattAvvik = 25.0
        )
        avviksvurderingProducer.finalize()
        assertEquals(1, testRapid.inspektør.size)
        val message = testRapid.inspektør.message(0)
        assertEquals("avviksvurdering", message["@event_name"].asText())
        assertEquals(fødselsnummer.value, message["fødselsnummer"].asText())
        assertEquals(aktørId.value, message["aktørId"].asText())
        assertEquals(skjæringstidspunkt, message["skjæringstidspunkt"].asLocalDate())
        assertPresent(message["avviksvurdering"])
        val avviksvurdering = message["avviksvurdering"]
        assertPresent(avviksvurdering["opprettet"])
        assertPresent(avviksvurdering["avviksprosent"])
        assertPresent(avviksvurdering["beregningsgrunnlag"])
        val beregningsgrunnlag = avviksvurdering["beregningsgrunnlag"]
        assertPresent(beregningsgrunnlag["totalbeløp"])
        assertPresent(beregningsgrunnlag["omregnedeÅrsinntekter"])
        val omregnedeÅrsinntekter = beregningsgrunnlag["omregnedeÅrsinntekter"]
        assertEquals(1, omregnedeÅrsinntekter.size())
        val enOmregnetÅrsinntekt = omregnedeÅrsinntekter.first()
        assertPresent(enOmregnetÅrsinntekt["arbeidsgiverreferanse"])
        assertPresent(enOmregnetÅrsinntekt["beløp"])
        assertPresent(avviksvurdering["sammenligningsgrunnlag"])
        val sammenligningsgrunnlag = avviksvurdering["sammenligningsgrunnlag"]
        assertPresent(sammenligningsgrunnlag["totalbeløp"])
        assertPresent(sammenligningsgrunnlag["innrapporterteInntekter"])
        val innrapporterteInntekter = sammenligningsgrunnlag["innrapporterteInntekter"]
        assertEquals(1, innrapporterteInntekter.size())
        val enInnrapportertInntekt = innrapporterteInntekter.first()
        assertPresent(enInnrapportertInntekt["arbeidsgiverreferanse"])
        assertPresent(enInnrapportertInntekt["inntekter"])
        val inntekter = enInnrapportertInntekt["inntekter"]
        assertEquals(1, inntekter.size())
        val enInntekt = inntekter.first()
        assertPresent(enInntekt["årMåned"])
        assertPresent(enInntekt["beløp"])
    }

    @Test
    fun `lager en avviksvurderingmelding`() {
        val arbeidsgiver1 = "987654321".somArbeidsgiverref()
        val arbeidsgiver2 = "987654322".somArbeidsgiverref()
        val omregnetÅrsinntekt1 = OmregnetÅrsinntekt(
            400000.0
        )
        val omregnetÅrsinntekt2 = OmregnetÅrsinntekt(
            400000.0
        )

        val avviksprosent = 24.9
        avviksvurderingProducer.avvikVurdert(
            avviksprosent = avviksprosent,
            harAkseptabeltAvvik = true,
            beregningsgrunnlag = Beregningsgrunnlag.opprett(
                mapOf(
                    arbeidsgiver1 to omregnetÅrsinntekt1,
                    arbeidsgiver2 to omregnetÅrsinntekt2
                )
            ),
            sammenligningsgrunnlag = Sammenligningsgrunnlag(
                listOf(
                    ArbeidsgiverInntekt(
                        arbeidsgiverreferanse = arbeidsgiver1,
                        inntekter = listOf(
                            ArbeidsgiverInntekt.MånedligInntekt(
                                inntekt = InntektPerMåned(30000.0),
                                måned = YearMonth.from(1.januar),
                                fordel = null,
                                beskrivelse = null,
                                inntektstype = ArbeidsgiverInntekt.Inntektstype.LØNNSINNTEKT
                            )
                        )
                    ),
                    ArbeidsgiverInntekt(
                        arbeidsgiverreferanse = arbeidsgiver2,
                        inntekter = listOf(
                            ArbeidsgiverInntekt.MånedligInntekt(
                                inntekt = InntektPerMåned(30000.0),
                                måned = YearMonth.from(1.januar),
                                fordel = null,
                                beskrivelse = null,
                                inntektstype = ArbeidsgiverInntekt.Inntektstype.LØNNSINNTEKT
                            )
                        )
                    )
                )
            ),
            maksimaltTillattAvvik = 25.0
        )
        avviksvurderingProducer.finalize()

        val message = testRapid.inspektør.message(0)
        val avviksvurdering = message["avviksvurdering"]
        assertEquals(avviksprosent, avviksvurdering["avviksprosent"].asDouble())
        val beregningsgrunnlag = avviksvurdering["beregningsgrunnlag"]
        assertEquals(800000.0, beregningsgrunnlag["totalbeløp"].asDouble())
        val omregnedeÅrsinntekter = beregningsgrunnlag["omregnedeÅrsinntekter"]
        assertEquals(2, omregnedeÅrsinntekter.size())
        val inntekt1 = omregnedeÅrsinntekter[0]
        assertEquals(arbeidsgiver1.value, inntekt1["arbeidsgiverreferanse"].asText())
        val inntekt2 = omregnedeÅrsinntekter[1]
        assertEquals(arbeidsgiver2.value, inntekt2["arbeidsgiverreferanse"].asText())

        val sammenligningsgrunnlag = avviksvurdering["sammenligningsgrunnlag"]
        assertEquals(60000.0, sammenligningsgrunnlag["totalbeløp"].asDouble())
        val innrapporterteInntekter = sammenligningsgrunnlag["innrapporterteInntekter"]
        val innrapportertInntekt1 = innrapporterteInntekter[0]
        assertEquals(arbeidsgiver1.value, innrapportertInntekt1["arbeidsgiverreferanse"].asText())
        val inntekter1 = innrapportertInntekt1["inntekter"]
        assertEquals(YearMonth.from(1.januar), inntekter1.first()["årMåned"].asYearMonth())
        assertEquals(30000.0, inntekter1.first()["beløp"].asDouble())

        val innrapportertInntekt2 = innrapporterteInntekter[1]
        assertEquals(arbeidsgiver2.value, innrapportertInntekt2["arbeidsgiverreferanse"].asText())
        val inntekter2 = innrapportertInntekt2["inntekter"]
        assertEquals(YearMonth.from(1.januar), inntekter2.first()["årMåned"].asYearMonth())
        assertEquals(30000.0, inntekter2.first()["beløp"].asDouble())
    }

    private fun assertPresent(jsonNode: JsonNode?) {
        Assertions.assertNotNull(jsonNode) { "Forventer at noden ikke er null" }
        jsonNode?.isMissingOrNull()?.let { Assertions.assertFalse(it) { "Forventer at noden ikke mangler" } }
    }
}