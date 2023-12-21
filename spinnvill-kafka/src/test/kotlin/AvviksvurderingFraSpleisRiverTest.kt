import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class AvviksvurderingFraSpleisRiverTest {

    private val testRapid = TestRapid()

    private val messageHandler = object : MessageHandler {
        var håndterteAvviksvurderinger = 0
            private set

        override fun håndter(utkastTilVedtakMessage: UtkastTilVedtakMessage) {}
        override fun håndter(sammenligningsgrunnlagMessage: SammenligningsgrunnlagMessage) {}
        override fun håndter(avviksvurderingerFraSpleisMessage: AvviksvurderingerFraSpleisMessage) {}
        override fun håndter(enAvviksvurderingFraSpleisMessage: EnAvviksvurderingFraSpleisMessage) {
            håndterteAvviksvurderinger++
        }
    }

    init {
        AvviksvurderingFraSpleisRiver(testRapid, messageHandler)
    }

    @Test
    fun `kan lese en avviksvurdering fra Spleis`() {
        testRapid.sendTestMessage(avviksprosentBeregnetEvent)
        Assertions.assertEquals(1, messageHandler.håndterteAvviksvurderinger)
    }

    @Language("JSON")
    private val avviksprosentBeregnetEvent = """
        {
          "@event_name": "avviksprosent_beregnet_event",
          "fødselsnummer": "12345678910",
          "skjæringstidspunkt": "2018-01-01",
          "vurderingstidspunkt": "2018-01-01T00:00:00.000",
          "vilkårsgrunnlagId": "291228ef-8b4e-4a1f-8065-ad7ed85acbb4",
          "avviksprosent": 0.0,
          "sammenligningsgrunnlagTotalbeløp": 50000.0,
          "beregningsgrunnlagTotalbeløp": 30000.0,
          "omregnedeÅrsinntekter": [
            {
              "orgnummer": "987654321",
              "beløp": 30000.0
            }
          ],
          "sammenligningsgrunnlag": [
            {
              "orgnummer": "987654321",
              "skatteopplysninger": [
                {
                  "beløp": 20000.0,
                  "måned": "2018-01",
                  "type": "LØNNSINNTEKT",
                  "fordel": "naturalytelse",
                  "beskrivelse": "skattepliktigDelForsikringer"
                },
                {
                  "beløp": 30000.0,
                  "måned": "2018-02",
                  "type": "LØNNSINNTEKT",
                  "fordel": "kontantytelse",
                  "beskrivelse": "fastloenn"
                }
              ]
            }
          ]
        }
    """.trimIndent()
}