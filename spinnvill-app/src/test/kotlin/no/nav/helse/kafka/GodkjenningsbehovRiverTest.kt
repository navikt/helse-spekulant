@file:Suppress("SameParameterValue")

package no.nav.helse.kafka

import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.helpers.januar
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GodkjenningsbehovRiverTest {

    private val testRapid = TestRapid()

    private val messageHandler = object: MessageHandler {
        val messages = mutableListOf<GodkjenningsbehovMessage>()
        override fun håndter(message: GodkjenningsbehovMessage) {
            messages.add(message)
        }

        override fun håndter(sammenligningsgrunnlagMessage: SammenligningsgrunnlagMessage) {}
    }

    private companion object {
        private const val FØDSELSNUMMER = "12345678910"
        private const val ORGANISASJONSNUMMER = "987654321"
        private val skjæringstidspunkt = 1.januar
    }

    init {
        GodkjenningsbehovRiver(testRapid, messageHandler)
    }

    @Test
    fun `les inn godkjenningsbehov`() {
        testRapid.sendTestMessage(utkastTilVedtakJson(FØDSELSNUMMER, ORGANISASJONSNUMMER, skjæringstidspunkt))
        val message = messageHandler.messages.single()

        assertEquals(FØDSELSNUMMER, message.fødselsnummer)
        assertEquals(skjæringstidspunkt, message.skjæringstidspunkt)
        assertEquals(mapOf(ORGANISASJONSNUMMER to 500000.0, "000000000" to 200000.20), message.beregningsgrunnlag)
    }

    @Test
    fun `leser ikke inn godkjenningsbehov med løsning`() {
        testRapid.sendTestMessage(utkastTilVedtakJsonMedLøsning(FØDSELSNUMMER, ORGANISASJONSNUMMER, skjæringstidspunkt))
        assertEquals(0, messageHandler.messages.size)
    }

    @Test
    fun `leser ikke inn godkjenningsbehov markert med behandletAvSpinnvill`() {
        testRapid.sendTestMessage(utkastTilVedtakJsonMedBehandletAvSpinnvill(
            FØDSELSNUMMER,
            ORGANISASJONSNUMMER,
            skjæringstidspunkt
        ))
        assertEquals(0, messageHandler.messages.size)
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

    private fun utkastTilVedtakJsonNode(
        fødselsnummer: String, organisasjonsnummer: String, skjæringstidspunkt: LocalDate
    ) = utkastTilVedtakJson(fødselsnummer, organisasjonsnummer, skjæringstidspunkt)
        .let(objectMapper::readTree) as ObjectNode

    private fun utkastTilVedtakJsonMedLøsning(
        fødselsnummer: String,
        organisasjonsnummer: String,
        skjæringstidspunkt: LocalDate
    ) = utkastTilVedtakJsonNode(fødselsnummer, organisasjonsnummer, skjæringstidspunkt)
        .med("@løsning" to "{}")
        .let(objectMapper::writeValueAsString)

    private fun utkastTilVedtakJsonMedBehandletAvSpinnvill(
        fødselsnummer: String,
        organisasjonsnummer: String,
        skjæringstidspunkt: LocalDate
    ) = utkastTilVedtakJsonNode(fødselsnummer, organisasjonsnummer, skjæringstidspunkt)
        .med("behandletAvSpinnvill" to "true")
        .let(objectMapper::writeValueAsString)
}

private fun ObjectNode.med(vararg felter: Pair<String, Any>): ObjectNode {
    felter.forEach { (key, value) -> replace(key, objectMapper.valueToTree(value)) }
    return this
}
