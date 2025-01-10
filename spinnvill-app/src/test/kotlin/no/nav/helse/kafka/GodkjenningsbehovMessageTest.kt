@file:Suppress("SameParameterValue")

package no.nav.helse.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.helpers.januar
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GodkjenningsbehovMessageTest {

    private val testRapid = TestRapid()
    private val messageHandler = object : MessageHandler {
        val messages = mutableListOf<GodkjenningsbehovMessage>()
        override fun håndter(message: GodkjenningsbehovMessage) {
            messages.add(message)
        }

        override fun håndter(sammenligningsgrunnlagMessage: SammenligningsgrunnlagMessage) {}
    }

    init {
        GodkjenningsbehovRiver(testRapid, messageHandler)
    }

    @Test
    fun `kan konvertere seg selv til map og markere seg som final`() {
        testRapid.sendTestMessage(
            utkastTilVedtakJson(
                "12345678910",
                "987654321",
                1.januar
            )
        )

        val melding = messageHandler.messages.first()

        assertNull(melding.toJson()["behandletAvSpinnvill"])
        assertEquals(true, melding.utgående()["behandletAvSpinnvill"])
    }

    private fun utkastTilVedtakJson(
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
