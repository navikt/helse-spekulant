@file:Suppress("SameParameterValue")

package no.nav.helse.mediator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asYearMonth
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.*
import no.nav.helse.db.TestDatabase
import no.nav.helse.dto.AvviksvurderingDto
import no.nav.helse.helpers.januar
import no.nav.helse.helpers.objectMapper
import no.nav.helse.kafka.asUUID
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class MediatorTest {
    private val testRapid = TestRapid()
    private val database = TestDatabase.database()

    private val FØDSELSNUMMER = "12345678910"
    private val ORGANISASJONSNUMMER = "987654321"
    private val SKJÆRINGSTIDSPUNKT = 1.januar
    private val BEREGNINGSGRUNNLAG = AvviksvurderingDto.BeregningsgrunnlagDto(
        mapOf(
            Arbeidsgiverreferanse(ORGANISASJONSNUMMER) to OmregnetÅrsinntekt(600000.0),
        )
    )

    init {
        Mediator(VersjonAvKode("1.0.0"), testRapid, ::database)
    }

    @BeforeEach
    fun beforeEach() {
        TestDatabase.reset()
    }

    @Test
    fun `sender behov for sammenligningsgrunnlag`() {
        mottaUtkastTilVedtak()

        val beregningsperiodeTom = YearMonth.from(SKJÆRINGSTIDSPUNKT.minusMonths(1))
        val beregningsperiodeFom = beregningsperiodeTom.minusMonths(11)

        assertEquals(1, testRapid.inspektør.size)
        assertEquals("behov", testRapid.inspektør.field(0, "@event_name").asText())
        assertEquals(
            beregningsperiodeFom,
            testRapid.inspektør.field(0, "InntekterForSammenligningsgrunnlag").path("beregningStart").asYearMonth()
        )
        assertEquals(
            beregningsperiodeTom,
            testRapid.inspektør.field(0, "InntekterForSammenligningsgrunnlag").path("beregningSlutt").asYearMonth()
        )
        assertEquals(
            SKJÆRINGSTIDSPUNKT,
            testRapid.inspektør.field(0, "InntekterForSammenligningsgrunnlag").path("skjæringstidspunkt").asLocalDate()
        )
    }

    @Test
    fun `ikke send utkast til vedtak ved behov for sammenligningsgrunnlag`() {
        mottaUtkastTilVedtak()

        assertEquals(1, testRapid.inspektør.size)
        assertNotEquals("Godkjenning", testRapid.inspektør.field(0, "@event_name").asText())
    }

    @Test
    fun `sendt utkast til vedtak ved avviksvurdering`() {
        mottaUtkastTilVedtak()
        mottaSammenligningsgrunnlag()

        val avviksvurderingId = database.finnSisteAvviksvurderingsgrunnlag(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT)!!.id
        testRapid.inspektør.assertGodkjenningsbehovHarAvviksvurderingId(avviksvurderingId)
    }


    @Test
    fun `sender utkast til vedtak hvis det ikke gjøres ny avviksvurdering`() {
        mottaUtkastTilVedtak()
        mottaSammenligningsgrunnlag()

        testRapid.reset()

        mottaUtkastTilVedtak()

        val avviksvurderingId = database.finnSisteAvviksvurderingsgrunnlag(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT)!!.id
        testRapid.inspektør.assertGodkjenningsbehovHarAvviksvurderingId(avviksvurderingId)
    }

    @Test
    fun `sender ikke behov for sammenligningsgrunnlag når det finnes en eksisterende avviksvurdering`() {
        mottaUtkastTilVedtak()
        mottaSammenligningsgrunnlag()

        testRapid.reset()

        mottaUtkastTilVedtak(
            beregningsgrunnlag = AvviksvurderingDto.BeregningsgrunnlagDto(
                mapOf(ORGANISASJONSNUMMER.somArbeidsgiverref() to OmregnetÅrsinntekt(900000.0))
            )
        )

        assertEquals(0, testRapid.inspektør.behov("InntekterForSammenligningsgrunnlag").size)
    }

    @Test
    fun `motta sammenligningsgrunnlag`() {
        mottaUtkastTilVedtak()
        mottaSammenligningsgrunnlag()

        assertNotNull(database.finnSisteAvviksvurderingsgrunnlag(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT))
    }

    @Test
    fun `gjør en vellykket avviksvurdering uten avvik`() {
        mottaUtkastTilVedtak()
        mottaSammenligningsgrunnlag()

        val fullstendigAvviksvurdering = database.finnSisteAvviksvurderingsgrunnlag(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT)

        assertNotNull(fullstendigAvviksvurdering)
        assertNotNull(fullstendigAvviksvurdering.beregningsgrunnlag)

        assertEquals(5, testRapid.inspektør.size)
        assertEquals(0, testRapid.inspektør.hendelser("nye_varsler").size)
        assertEquals(1, testRapid.inspektør.behov("InntekterForSammenligningsgrunnlag").size)
        assertEquals(2, testRapid.inspektør.hendelser("subsumsjon").size)
        assertEquals(1, testRapid.inspektør.hendelser("avvik_vurdert").size)
        assertEquals(1, testRapid.inspektør.behov("Godkjenning").size)
    }

    @Test
    fun `gjør en vellykket avviksvurdering med avvik`() {
        mottaUtkastTilVedtak()
        mottaSammenligningsgrunnlag(årsinntekt = 90000.0)

        val fullstendigAvviksvurdering = database.finnSisteAvviksvurderingsgrunnlag(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT)

        assertNotNull(fullstendigAvviksvurdering)
        assertNotNull(fullstendigAvviksvurdering.beregningsgrunnlag)

        assertEquals(5, testRapid.inspektør.size)
        assertEquals(1, testRapid.inspektør.hendelser("nye_varsler").size)
        assertEquals(1, testRapid.inspektør.behov("InntekterForSammenligningsgrunnlag").size)
        assertEquals(1, testRapid.inspektør.hendelser("subsumsjon").size)
        assertEquals(1, testRapid.inspektør.hendelser("avvik_vurdert").size)
        assertEquals(1, testRapid.inspektør.behov("Godkjenning").size)
    }

    @Test
    fun `gjør ikke ny avviksvurdering om beregningsgrunnlaget er likt som for forrige avviksvurdering`() {
        mottaUtkastTilVedtak()
        mottaSammenligningsgrunnlag()

        testRapid.reset()

        val avviksvurdering = database.finnSisteAvviksvurderingsgrunnlag(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT)

        mottaUtkastTilVedtak()

        val sisteAvviksvurdering = database.finnSisteAvviksvurderingsgrunnlag(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT)

        assertEquals(avviksvurdering, sisteAvviksvurdering)
        assertEquals(1, testRapid.inspektør.size)
        assertNotNull(testRapid.inspektør.sisteBehovAvType("Godkjenning"))
        assertNull(testRapid.inspektør.sisteBehovAvType("InntekterForSammenligningsgrunnlag"))
    }

    @Test
    fun `gjør ny avviksvurdering om beregningsgrunnlaget er forskjellig fra forrige avviksvurdering`() {
        mottaUtkastTilVedtak()
        mottaSammenligningsgrunnlag()

        testRapid.reset()
        val avviksvurdering = database.finnSisteAvviksvurderingsgrunnlag(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT)

        mottaUtkastTilVedtak(
            beregningsgrunnlag = AvviksvurderingDto.BeregningsgrunnlagDto(
                mapOf(ORGANISASJONSNUMMER.somArbeidsgiverref() to OmregnetÅrsinntekt(900000.0))
            )
        )

        val sisteAvviksvurdering = database.finnSisteAvviksvurderingsgrunnlag(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT)

        assertNotNull(avviksvurdering)
        assertNotNull(sisteAvviksvurdering)
        assertNotEquals(avviksvurdering.id, sisteAvviksvurdering.id)
        assertNotEquals(avviksvurdering.beregningsgrunnlag, sisteAvviksvurdering.beregningsgrunnlag)
        assertEquals(avviksvurdering.fødselsnummer, sisteAvviksvurdering.fødselsnummer)
        assertEquals(avviksvurdering.skjæringstidspunkt, sisteAvviksvurdering.skjæringstidspunkt)
        assertEquals(avviksvurdering.sammenligningsgrunnlag, sisteAvviksvurdering.sammenligningsgrunnlag)
    }

    @Test
    fun `bruker ikke nytt sammenligningsgrunnlag hvis vi har en eksisterende avviksvurdering for samme fnr og skjæringstidspunkt`() {
        mottaUtkastTilVedtak()

        mottaSammenligningsgrunnlag()
        val avviksvurdering1 = database.finnSisteAvviksvurderingsgrunnlag(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT)

        mottaSammenligningsgrunnlag()
        val avviksvurdering2 = database.finnSisteAvviksvurderingsgrunnlag(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT)

        assertEquals(avviksvurdering1, avviksvurdering2)
    }

    @Test
    fun `meldinger skal sendes i riktig rekkefølge`() {
        mottaUtkastTilVedtak()
        mottaSammenligningsgrunnlag()

        assertEquals(5, testRapid.inspektør.size)
        assertEquals("InntekterForSammenligningsgrunnlag", testRapid.inspektør.field(0, "@behov").first().asText())
        assertEquals("subsumsjon", testRapid.inspektør.field(1, "@event_name").asText())
        assertEquals("subsumsjon", testRapid.inspektør.field(2, "@event_name").asText())
        assertEquals("avvik_vurdert", testRapid.inspektør.field(3, "@event_name").asText())
        assertEquals("Godkjenning", testRapid.inspektør.field(4, "@behov").first().asText())
    }

    private fun mottaUtkastTilVedtak(beregningsgrunnlag: AvviksvurderingDto.BeregningsgrunnlagDto = BEREGNINGSGRUNNLAG) {
        testRapid.sendTestMessage(
            utkastTilVedtakJson(
                FØDSELSNUMMER,
                ORGANISASJONSNUMMER,
                SKJÆRINGSTIDSPUNKT,
                beregningsgrunnlag
            )
        )
    }

    private fun mottaSammenligningsgrunnlag(årsinntekt: Double = 600000.0, antallMåneder: Int = 12) {
        val behov = testRapid.inspektør.sisteBehovAvType("InntekterForSammenligningsgrunnlag") as? ObjectNode
        assertNotNull(behov)

        val inntekterForSammenligningsgrunnlag =
            objectMapper.convertValue(mapOf("InntekterForSammenligningsgrunnlag" to List(antallMåneder) {
                mapOf(
                    "årMåned" to YearMonth.of(2018, it + 1),
                    "inntektsliste" to listOf(
                        mapOf<String, Any>(
                            "beløp" to årsinntekt / antallMåneder,
                            "inntektstype" to "LOENNSINNTEKT",
                            "orgnummer" to ORGANISASJONSNUMMER
                        )
                    )
                )
            }), JsonNode::class.java)

        val løsning = behov
            .set<ObjectNode>("@løsning", inntekterForSammenligningsgrunnlag)
            .put("@final", true)

        testRapid.sendTestMessage(objectMapper.writeValueAsString(løsning))
    }

    private fun TestRapid.RapidInspector.meldinger() =
        (0 until size).map { index -> message(index) }

    private fun TestRapid.RapidInspector.hendelser(type: String) =
        meldinger().filter { it.path("@event_name").asText() == type }

    private fun TestRapid.RapidInspector.sisteBehovAvType(vararg behov: String) =
        hendelser("behov").lastOrNull {
            it.path("@behov").map(JsonNode::asText).containsAll(behov.toList()) && !it.hasNonNull("@løsning")
        }

    private fun TestRapid.RapidInspector.behov(behov: String) =
        hendelser("behov")
            .filter { it.path("@behov").map(JsonNode::asText).containsAll(listOf(behov)) }

    private fun TestRapid.RapidInspector.assertGodkjenningsbehovHarAvviksvurderingId(id: UUID) =
        assertEquals(id, behov("Godkjenning").single()["avviksvurderingId"].asUUID())

    private fun utkastTilVedtakJson(
        fødselsnummer: String,
        organisasjonsnummer: String,
        skjæringstidspunkt: LocalDate,
        beregningsgrunnlagDto: AvviksvurderingDto.BeregningsgrunnlagDto = AvviksvurderingDto.BeregningsgrunnlagDto(
            mapOf(
                Arbeidsgiverreferanse(organisasjonsnummer) to OmregnetÅrsinntekt(500000.0),
                Arbeidsgiverreferanse("000000000") to OmregnetÅrsinntekt(200000.20)
            )
        )
    ): String {
        @Language("JSON")
        val json = """
            {
              "@event_name": "behov",
              "@behovId": "c64a73be-7337-4f25-8923-94f355c23d76",
              "@behov": [
                "Godkjenning"
              ],
              "meldingsreferanseId": "b63537e5-ffd9-4e9b-930c-45b0ab602d66",
              "fødselsnummer": "$fødselsnummer",
              "organisasjonsnummer": "$organisasjonsnummer",
              "vedtaksperiodeId": "d6a1575f-a241-4338-baea-26df557f7506",
              "tilstand": "AVVENTER_GODKJENNING",
              "utbetalingId": "db8ff403-6ea3-4ad2-bfdf-d876e28c5839",
              "Godkjenning": {
                "periodeFom": "$skjæringstidspunkt",
                "periodeTom": "${skjæringstidspunkt.plusDays(30)}",
                "skjæringstidspunkt": "$skjæringstidspunkt",
                "vilkårsgrunnlagId": "87b9339d-a67d-49b0-af36-c93d6f9249ae",
                "periodetype": "FØRSTEGANGSBEHANDLING",
                "førstegangsbehandling": true,
                "utbetalingtype": "UTBETALING",
                "inntektskilde": "EN_ARBEIDSGIVER",
                "orgnummereMedRelevanteArbeidsforhold": [],
                "tags": [
                  "EN_ARBEIDSGIVER",
                  "ARBEIDSGIVERUTBETALING"
                ],
                "kanAvvises": true,
                "omregnedeÅrsinntekter": ${beregningsgrunnlagDto.toJson()} 
              },
              "@id": "ba376523-62b1-49d7-8647-f902c739b634",
              "@opprettet": "2018-01-01T00:00:00.000"
            }
        """.trimIndent()
        return json
    }

    private fun AvviksvurderingDto.BeregningsgrunnlagDto.toJson(): String {
        return objectMapper.writeValueAsString(this.omregnedeÅrsinntekter.map {
            mapOf(
                "organisasjonsnummer" to it.key,
                "beløp" to it.value
            )
        })
    }

}
