package no.nav.helse.kafka

import no.nav.helse.helpers.januar
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UtkastTilVedtakRiverTest {

    private val testRapid = TestRapid()

    private val messageHandler = object: MessageHandler {
        val messages = mutableListOf<UtkastTilVedtakMessage>()
        override fun håndter(utkastTilVedtakMessage: UtkastTilVedtakMessage) {
            messages.add(utkastTilVedtakMessage)
        }

        override fun håndter(sammenligningsgrunnlagMessage: SammenligningsgrunnlagMessage) {}
    }
//teste hva hvis fødselsnumemr osv ikke er der blablab
    private companion object {
        private const val AKTØRID = "1234567891011"
        private const val FØDSELSNUMMER = "12345678910"
        private const val ORGANISASJONSNUMMER = "987654321"
        private val skjæringstidspunkt = 1.januar
    }
    init {
        UtkastTilVedtakRiver(testRapid, messageHandler)
    }
    @Test
    fun `les inn godkjenningsbehov`() {
        testRapid.sendTestMessage(utkastTilVedtakJson(AKTØRID, FØDSELSNUMMER, ORGANISASJONSNUMMER, skjæringstidspunkt))
        val message = messageHandler.messages.single()

        assertEquals(FØDSELSNUMMER, message.fødselsnummer)
        assertEquals(skjæringstidspunkt, message.skjæringstidspunkt)
        assertEquals(mapOf(ORGANISASJONSNUMMER to 500000.0, "000000000" to 200000.20), message.beregningsgrunnlag)
    }

    @Test
    fun `leser ikke inn godkjenningsbehov med løsning`() {
        testRapid.sendTestMessage(utkastTilVedtakJsonMedLøsning(AKTØRID, FØDSELSNUMMER, ORGANISASJONSNUMMER, skjæringstidspunkt))
        assertEquals(0, messageHandler.messages.size)
    }

    private fun utkastTilVedtakJson(
        aktørId: String,
        fødselsnummer: String,
        organisasjonsnummer: String,
        skjæringstidspunkt: LocalDate
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
              "aktørId": "$aktørId",
              "fødselsnummer": "$fødselsnummer",
              "organisasjonsnummer": "$organisasjonsnummer",
              "vedtaksperiodeId": "d6a1575f-a241-4338-baea-26df557f7506",
              "tilstand": "AVVENTER_GODKJENNING",
              "utbetalingId": "db8ff403-6ea3-4ad2-bfdf-d876e28c5839",
              "Godkjenning": {
                "periodeFom": "$skjæringstidspunkt",
                "periodeTom": "${skjæringstidspunkt.plusDays(30)}",
                "skjæringstidspunkt": "$skjæringstidspunkt",
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
                "omregnedeÅrsinntekter": [
                  {
                    "organisasjonsnummer": "$organisasjonsnummer",
                    "beløp": 500000.0
                  },
                  {
                    "organisasjonsnummer": "000000000",
                    "beløp": 200000.20
                  }
                ]
              },
              "@id": "ba376523-62b1-49d7-8647-f902c739b634",
              "@opprettet": "2018-01-01T00:00:00.000"
            }
        """.trimIndent()
        return json
    }

    private fun utkastTilVedtakJsonMedLøsning(
        aktørId: String,
        fødselsnummer: String,
        organisasjonsnummer: String,
        skjæringstidspunkt: LocalDate
    ): String {
        @Language("JSON")
        val json = """
            {
              "@event_name": "behov",
              "@behovId": "c64a73be-7337-4f25-8923-94f355c23d76",
              "@behov": [
                "Godkjenning"
              ],
              "@løsning": {},
              "meldingsreferanseId": "b63537e5-ffd9-4e9b-930c-45b0ab602d66",
              "aktørId": "$aktørId",
              "fødselsnummer": "$fødselsnummer",
              "organisasjonsnummer": "$organisasjonsnummer",
              "vedtaksperiodeId": "d6a1575f-a241-4338-baea-26df557f7506",
              "tilstand": "AVVENTER_GODKJENNING",
              "utbetalingId": "db8ff403-6ea3-4ad2-bfdf-d876e28c5839",
              "Godkjenning": {
                "periodeFom": "$skjæringstidspunkt",
                "periodeTom": "${skjæringstidspunkt.plusDays(30)}",
                "skjæringstidspunkt": "$skjæringstidspunkt",
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
                "omregnedeÅrsinntekter": [
                  {
                    "organisasjonsnummer": "$organisasjonsnummer",
                    "beløp": 500000.0
                  },
                  {
                    "organisasjonsnummer": "000000000",
                    "beløp": 200000.20
                  }
                ]
              },
              "@id": "ba376523-62b1-49d7-8647-f902c739b634",
              "@opprettet": "2018-01-01T00:00:00.000"
            }
        """.trimIndent()
        return json
    }
}