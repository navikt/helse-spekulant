import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class AvviksvurderingerFraSpleisRiverTest {

    private val testRapid = TestRapid()

    private val messageHandler = object: MessageHandler {
        var håndterteAvviksvurderinger = 0
            private set
        override fun håndter(utkastTilVedtakMessage: UtkastTilVedtakMessage) = TODO("Not yet implemented")
        override fun håndter(sammenligningsgrunnlagMessage: SammenligningsgrunnlagMessage) = TODO("Not yet implemented")
        override fun håndter(avviksvurderingerFraSpleisMessage: AvviksvurderingerFraSpleisMessage) {
            håndterteAvviksvurderinger++
        }

        override fun håndter(enAvviksvurderingFraSpleisMessage: EnAvviksvurderingFraSpleisMessage) {}
    }

    init {
        AvviksvurderingerFraSpleisRiver(testRapid, messageHandler)
    }

    @Test
    fun `kan lese avviksvurderinger`() {
        testRapid.sendTestMessage(avviksvurderingerMelding)
        Assertions.assertEquals(1, messageHandler.håndterteAvviksvurderinger)
    }

    @Language("JSON")
    private val avviksvurderingerMelding = """
        {
          "@event_name": "avviksvurderinger",
          "fødselsnummer": "12345678910",
          "skjæringstidspunkter": [
            {
              "skjæringstidspunkt": "2018-01-01",
              "vurderingstidspunkt": "2018-01-01T00:00:00.000",
              "vilkårsgrunnlagId": "291228ef-8b4e-4a1f-8065-ad7ed85acbb4",
              "avviksprosent": 0.0,
              "sammenligningsgrunnlagTotalbeløp": 50000.0,
              "beregningsgrunnlagTotalbeløp": 30000.0,
              "type": "SPLEIS",
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
            },
            {
              "skjæringstidspunkt": "2018-02-01",
              "vurderingstidspunkt": "1970-01-01T00:00:00",
              "vilkårsgrunnlagId": "291228ef-8b4e-4a1f-8065-ad7ed85acbb4",
              "type": "INFOTRYGD",
              "omregnedeÅrsinntekter": [],
              "sammenligningsgrunnlag": []
            },
            {
              "skjæringstidspunkt": "2018-03-01",
              "vurderingstidspunkt": "2018-03-01T00:00:00.000",
              "vilkårsgrunnlagId": "1862266e-3a75-4f1e-8cfa-6ecd9d8bf172",
              "avviksprosent": 0.0,
              "sammenligningsgrunnlagTotalbeløp": 30000.0,
              "beregningsgrunnlagTotalbeløp": 30000.0,
              "type": "SPLEIS",
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
                      "beløp": 15000.0,
                      "måned": "2017-10",
                      "type": "LØNNSINNTEKT",
                      "fordel": "naturalytelse",
                      "beskrivelse": "skattepliktigDelForsikringer"
                    },
                    {
                      "beløp": 15000.0,
                      "måned": "2017-11",
                      "type": "LØNNSINNTEKT",
                      "fordel": "naturalytelse",
                      "beskrivelse": "skattepliktigDelForsikringer"
                    },
                    {
                      "beløp": 50000.0,
                      "måned": "2017-12",
                      "type": "LØNNSINNTEKT",
                      "fordel": "kontantytelse",
                      "beskrivelse": "fastloenn"
                    }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()
}