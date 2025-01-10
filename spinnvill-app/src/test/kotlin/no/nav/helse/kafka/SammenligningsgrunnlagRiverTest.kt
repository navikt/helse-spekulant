@file:Suppress("SameParameterValue")

package no.nav.helse.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.helpers.januar
import no.nav.helse.helpers.objectMapper
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

class SammenligningsgrunnlagRiverTest {

    private val testRapid = TestRapid()

    private val messageHandler = object : MessageHandler {
        val messages = mutableListOf<SammenligningsgrunnlagMessage>()

        override fun håndter(message: GodkjenningsbehovMessage) {}

        override fun håndter(sammenligningsgrunnlagMessage: SammenligningsgrunnlagMessage) {
            messages.add(sammenligningsgrunnlagMessage)
        }

    }

    private companion object {
        private const val FØDSELSNUMMER = "12345678910"
        private const val ORGANISASJONSNUMMER = "987654321"
    }

    init {
        SammenligningsgrunnlagRiver(testRapid, messageHandler)
    }

    @Test
    fun `Leser inn sammenligningsgrunnlag løsning`() {
        testRapid.sendTestMessage(
            sammenligningsgrunnlagJsonMed(
                fødselsnummer = FØDSELSNUMMER,
                organisasjonsnummer = ORGANISASJONSNUMMER,
                skjæringstidspunkt = 1.januar,
                inntekter = inntekterForSammenligningsgrunnlag(
                    YearMonth.of(2023, 1) to listOf(inntekt())
                )
            )
        )
        assertEquals(1, messageHandler.messages.size)
    }

    @Test
    fun `Leser ikke inn sammenligningsgrunnlag uten løsning`() {
        testRapid.sendTestMessage(
            sammenligningsgrunnlagJsonUtenLøsning(
                FØDSELSNUMMER,
                ORGANISASJONSNUMMER,
                1.januar
            )
        )
        assertEquals(0, messageHandler.messages.size)
    }

    @Test
    fun `Leser ikke inn sammenligningsgrunnlag som mangler årMåned`() {
        testRapid.sendTestMessage(
            sammenligningsgrunnlagJsonMed(
                FØDSELSNUMMER,
                ORGANISASJONSNUMMER,
                1.januar,
                inntekterForSammenligningsgrunnlag(null to emptyList())
            )
        )
        assertEquals(0, messageHandler.messages.size)
    }

    @Test
    fun `Leser ikke inn sammenligningsgrunnlag som mangler inntektsliste`() {
        testRapid.sendTestMessage(
            sammenligningsgrunnlagJsonMed(
                fødselsnummer = FØDSELSNUMMER,
                organisasjonsnummer = ORGANISASJONSNUMMER,
                skjæringstidspunkt = 1.januar,
                inntekter = inntekterForSammenligningsgrunnlag(YearMonth.of(2023, 1) to null)
            )
        )
        assertEquals(0, messageHandler.messages.size)
    }

    @Test
    fun `Leser inn sammenligningsgrunnlag uten beskrivelse`() {
        testRapid.sendTestMessage(
            sammenligningsgrunnlagJsonMed(
                fødselsnummer = FØDSELSNUMMER,
                organisasjonsnummer = ORGANISASJONSNUMMER,
                skjæringstidspunkt = 1.januar,
                inntekter = inntekterForSammenligningsgrunnlag(
                    YearMonth.of(2023, 1) to listOf(inntekt(fordel = null))
                )
            )
        )
        assertEquals(1, messageHandler.messages.size)
    }

    @Test
    fun `Leser ikke inn sammenligningsgrunnlag uten inntektstype`() {
        testRapid.sendTestMessage(
            sammenligningsgrunnlagJsonMed(
                fødselsnummer = FØDSELSNUMMER,
                organisasjonsnummer = ORGANISASJONSNUMMER,
                skjæringstidspunkt = 1.januar,
                inntekterForSammenligningsgrunnlag(
                    YearMonth.of(2023, 1) to listOf(inntekt(inntektstype = null))
                )
            )
        )
        assertEquals(0, messageHandler.messages.size)
    }

    @Test
    fun `Leser ikke inn sammenligningsgrunnlag uten orgnummer eller fødselsnummer`() {
        assertThrows<IllegalStateException> {
            testRapid.sendTestMessage(
                sammenligningsgrunnlagJsonMed(
                    fødselsnummer = FØDSELSNUMMER,
                    organisasjonsnummer = ORGANISASJONSNUMMER,
                    skjæringstidspunkt = 1.januar,
                    inntekterForSammenligningsgrunnlag(
                        YearMonth.of(2023, 1) to listOf(inntekt(arbeidsgiverMangler = true))
                    )
                )
            )
        }
        assertEquals(0, messageHandler.messages.size)
    }

    @Test
    fun `Leser ikke inn sammenligningsgrunnlag uten beløp`() {
        testRapid.sendTestMessage(
            sammenligningsgrunnlagJsonMed(
                fødselsnummer = FØDSELSNUMMER,
                organisasjonsnummer = ORGANISASJONSNUMMER,
                skjæringstidspunkt = 1.januar,
                inntekterForSammenligningsgrunnlag(
                    YearMonth.of(2023, 1) to listOf(inntekt(beløp = null))
                )
            )
        )
        assertEquals(0, messageHandler.messages.size)
    }

    @Test
    fun `Leser ikke inn sammenligningsgrunnlag hvis inntektstype ikke er gyldig`() {
        testRapid.sendTestMessage(
            sammenligningsgrunnlagJsonMed(
                fødselsnummer = FØDSELSNUMMER,
                organisasjonsnummer = ORGANISASJONSNUMMER,
                skjæringstidspunkt = 1.januar,
                inntekterForSammenligningsgrunnlag(
                    YearMonth.of(2023, 1) to listOf(inntekt(inntektstype = "NOE ANNET"))
                )
            )
        )
        assertEquals(0, messageHandler.messages.size)
    }

    @Test
    fun `Leser ikke inn sammenligningsgrunnlag hvis det ikke er final`() {
        testRapid.sendTestMessage(
            sammenligningsgrunnlagJsonMed(
                fødselsnummer = FØDSELSNUMMER,
                organisasjonsnummer = ORGANISASJONSNUMMER,
                skjæringstidspunkt = 1.januar,
                inntekter = inntekterForSammenligningsgrunnlag(
                    YearMonth.of(2023, 1) to listOf(inntekt(fordel = null))
                ),
                final = false
            )
        )
        testRapid.sendTestMessage(
            sammenligningsgrunnlagJsonMed(
                fødselsnummer = FØDSELSNUMMER,
                organisasjonsnummer = ORGANISASJONSNUMMER,
                skjæringstidspunkt = 1.januar,
                inntekter = inntekterForSammenligningsgrunnlag(
                    YearMonth.of(2023, 1) to listOf(inntekt(fordel = null))
                )
            )
        )
        assertEquals(1, messageHandler.messages.size)
    }

    private fun inntekterForSammenligningsgrunnlag(
        vararg inntekter: Pair<YearMonth?, List<Inntekt>?>
    ): List<InntektForSammenligningsgrunnlag> {
        return inntekter.map { (yearMonth, inntekter) ->
            InntektForSammenligningsgrunnlag(yearMonth, inntekter)
        }
    }

    private fun inntekt(
        beløp: Double? = 20000.0,
        inntektstype: String? = "LOENNSINNTEKT",
        arbeidsgiverMangler: Boolean = false,
        beskrivelse: String? = "En beskrivelse",
        fordel: String? = "En fordel"
    ): Inntekt {
        return Inntekt(
            beløp = beløp,
            inntektstype = inntektstype,
            orgnummer = if (arbeidsgiverMangler) null else "987654321",
            fødselsnummer = if (arbeidsgiverMangler) null else "12345678910",
            beskrivelse = beskrivelse,
            fordel = fordel
        )
    }

    private data class InntektForSammenligningsgrunnlag(
        val årMåned: YearMonth?,
        val inntektsliste: List<Inntekt>?
    )

    private data class Inntekt(
        val beløp: Double?,
        val inntektstype: String?,
        val orgnummer: String?,
        val fødselsnummer: String?,
        val beskrivelse: String?,
        val fordel: String?
    )

    private fun List<InntektForSammenligningsgrunnlag>.toJson(): String {
        return objectMapper.writeValueAsString(this)
    }

    private fun sammenligningsgrunnlagJsonMed(
        fødselsnummer: String,
        organisasjonsnummer: String,
        skjæringstidspunkt: LocalDate,
        inntekter: List<InntektForSammenligningsgrunnlag>,
        final: Boolean = true
    ): String {
        @Language("JSON")
        val json = """
            {
              "@event_name": "behov",
              "@behovId": "ed8f2e02-15b1-45a7-88e4-3b2f0b9cda73",
              "@behov": [
                "InntekterForSammenligningsgrunnlag"
              ],
              "meldingsreferanseId": "ff032457-203f-43ec-8850-b72a57ad9e52",
              "fødselsnummer": "$fødselsnummer",
              "organisasjonsnummer": "$organisasjonsnummer",
              "vedtaksperiodeId": "d6a1575f-a241-4338-baea-26df557f7506",
              "utkastTilVedtak": {},
              "InntekterForSammenligningsgrunnlag": {
                "skjæringstidspunkt": "$skjæringstidspunkt",
                "beregningStart": "2018-01",
                "beregningSlutt": "2018-02"
              },
              "@id": "ecfe47f6-2063-451a-b7e1-182490cc3153",
              "@opprettet": "2018-01-01T00:00:00.000",
              "@løsning": {
                "InntekterForSammenligningsgrunnlag": ${inntekter.toJson()}
              },
              "@final": $final
            }
        """.trimIndent()
        return json
    }

    private fun sammenligningsgrunnlagJsonUtenLøsning(
        fødselsnummer: String,
        organisasjonsnummer: String,
        skjæringstidspunkt: LocalDate
    ): String {
        @Language("JSON")
        val json = """
            {
              "@event_name": "behov",
              "@behovId": "ed8f2e02-15b1-45a7-88e4-3b2f0b9cda73",
              "@behov": [
                "InntekterForSammenligningsgrunnlag"
              ],
              "meldingsreferanseId": "ff032457-203f-43ec-8850-b72a57ad9e52",
              "fødselsnummer": "$fødselsnummer",
              "organisasjonsnummer": "$organisasjonsnummer",
              "vedtaksperiodeId": "d6a1575f-a241-4338-baea-26df557f7506",
              "utkastTilVedtak": {},
              "InntekterForSammenligningsgrunnlag": {
                "skjæringstidspunkt": "$skjæringstidspunkt",
                "beregningStart": "2018-01",
                "beregningSlutt": "2018-02"
              },
              "@id": "ecfe47f6-2063-451a-b7e1-182490cc3153",
              "@opprettet": "2018-01-01T00:00:00.000"
            }
        """.trimIndent()
        return json
    }
}
