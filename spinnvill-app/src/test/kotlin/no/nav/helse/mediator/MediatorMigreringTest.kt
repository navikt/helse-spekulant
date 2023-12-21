@file:Suppress("SameParameterValue")

package no.nav.helse.mediator

import Avviksvurderingkilde
import no.nav.helse.VersjonAvKode
import no.nav.helse.db.TestDatabase
import no.nav.helse.dto.AvviksvurderingDto
import no.nav.helse.helpers.januar
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.somFnr
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MediatorMigreringTest {
    private val testRapid = TestRapid()
    private val database = TestDatabase.database()

    private val AKTØR_ID = "1234567891011"
    private val FØDSELSNUMMER = "12345678910"
    private val ORGANISASJONSNUMMER = "987654321"
    private val SKJÆRINGSTIDSPUNKT = 1.januar

    init {
        Mediator(VersjonAvKode("1.0.0"), testRapid, database)
    }

    @BeforeEach
    fun beforeEach() {
        TestDatabase.reset()
    }

    @Test
    fun `lagre avviksvurdering fra Spleis`() {
        mottaAvviksvurderingerEventFraSpleis(kilde = Avviksvurderingkilde.SPLEIS)
        val avviksvurdering = database.finnSisteAvviksvurdering(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT)
        assertNotNull(avviksvurdering)
    }

    @Test
    fun `lagre avviksvurdering fra Spleis der vurderingen er gjort i Infotrygd`() {
        mottaAvviksvurderingerEventFraSpleis(kilde = Avviksvurderingkilde.INFOTRYGD)
        val avviksvurdering = database.finnSisteAvviksvurdering(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT)
        assertNotNull(avviksvurdering)
    }

    @Test
    fun `lagrer ikke avviksvurdering med samme vilkårsgrunnlagid flere ganger`() {
        val vilkårsgrunnlagId = UUID.randomUUID()
        mottaAvviksvurderingerEventFraSpleis(kilde = Avviksvurderingkilde.INFOTRYGD, vilkårsgrunnlagId)
        mottaAvviksvurderingerEventFraSpleis(kilde = Avviksvurderingkilde.SPLEIS, vilkårsgrunnlagId)
        val avviksvurdering = database.finnSisteAvviksvurdering(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT)
        assertNotNull(avviksvurdering)
        assertEquals(AvviksvurderingDto.KildeDto.INFOTRYGD, avviksvurdering.kilde)
    }

    @Test
    fun `hopper kun ut av gjeldende iterasjon dersom vilkårsgrunnlagiden finnes allerede`() {
        val vilkårsgrunnlagId = UUID.randomUUID()
        val annenVilkårsgurnnlagId = UUID.randomUUID()
        mottaAvviksvurderingerEventFraSpleis(kilde = Avviksvurderingkilde.INFOTRYGD, vilkårsgrunnlagId)
        mottaFlereAvviksvurderingerFraSpleis(AvviksvurderingFraSpleis(vilkårsgrunnlagId, SKJÆRINGSTIDSPUNKT), AvviksvurderingFraSpleis(annenVilkårsgurnnlagId, SKJÆRINGSTIDSPUNKT.minusDays(1)))

        val avviksvurdering = database.finnSisteAvviksvurdering(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT)
        val annenAvviksvurdering = database.finnSisteAvviksvurdering(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT.minusDays(1))

        assertNotNull(avviksvurdering)
        assertEquals(AvviksvurderingDto.KildeDto.INFOTRYGD, avviksvurdering.kilde)

        assertNotNull(annenAvviksvurdering)
        assertEquals(AvviksvurderingDto.KildeDto.SPLEIS, annenAvviksvurdering.kilde)
    }

    @Test
    fun `sender ut avvik_vurdert for avviksvurdering gjort i Spleis`() {
        val vilkårsgrunnlagId = UUID.randomUUID()
        mottaAvviksvurderingerEventFraSpleis(kilde = Avviksvurderingkilde.SPLEIS, vilkårsgrunnlagId)

        assertEquals(1, testRapid.inspektør.size)
        assertEquals("avvik_vurdert", testRapid.inspektør.field(0, "@event_name").asText())
    }

    @Test
    fun `sender ut ett avvik_vurdert per avviksvurdering gjort i Spleis`() {
        mottaFlereAvviksvurderingerFraSpleis(
            AvviksvurderingFraSpleis(UUID.randomUUID(), SKJÆRINGSTIDSPUNKT),
            AvviksvurderingFraSpleis(UUID.randomUUID(), SKJÆRINGSTIDSPUNKT.minusDays(1))
        )

        assertEquals(2, testRapid.inspektør.size)
        assertEquals("avvik_vurdert", testRapid.inspektør.field(0, "@event_name").asText())
        assertEquals("avvik_vurdert", testRapid.inspektør.field(1, "@event_name").asText())
    }

    @Test
    fun `sender ikke ut event for avviksvurderinger gjort i Infotrygd`() {
        val vilkårsgrunnlagId = UUID.randomUUID()
        mottaAvviksvurderingerEventFraSpleis(kilde = Avviksvurderingkilde.INFOTRYGD, vilkårsgrunnlagId)

        assertEquals(0, testRapid.inspektør.size)
    }


    @Test
    fun `lagre en avviksvurdering fra Spleis`() {
        val vilkårsgrunnlagId = UUID.randomUUID()
        mottaAvviksvurderingFraSpleis(AvviksvurderingFraSpleis(vilkårsgrunnlagId, SKJÆRINGSTIDSPUNKT))
        val avviksvurdering = database.finnSisteAvviksvurdering(FØDSELSNUMMER.somFnr(), SKJÆRINGSTIDSPUNKT)
        assertNotNull(avviksvurdering)

        assertEquals(1, testRapid.inspektør.size)
        assertEquals("avvik_vurdert", testRapid.inspektør.field(0, "@event_name").asText())
    }

    private fun mottaAvviksvurderingerEventFraSpleis(kilde: Avviksvurderingkilde, vilkårsgrunnlagId: UUID = UUID.randomUUID()) {
        val message = when (kilde) {
            Avviksvurderingkilde.SPLEIS -> avviksvurderingFraSpleisJson(AKTØR_ID, FØDSELSNUMMER, ORGANISASJONSNUMMER, SKJÆRINGSTIDSPUNKT, vilkårsgrunnlagId)
            Avviksvurderingkilde.INFOTRYGD -> avviksvurderingFraInfotrygdJson(AKTØR_ID, FØDSELSNUMMER, SKJÆRINGSTIDSPUNKT)
        }
        testRapid.sendTestMessage(message)
    }

    private fun mottaFlereAvviksvurderingerFraSpleis(vararg avviksvurderinger: AvviksvurderingFraSpleis) {
        val message = flereAvviksvurderingerFraSpleisJson(AKTØR_ID, FØDSELSNUMMER, ORGANISASJONSNUMMER, *avviksvurderinger)
        testRapid.sendTestMessage(message)
    }

    private fun mottaAvviksvurderingFraSpleis(enAvviksvurdering: AvviksvurderingFraSpleis) {
        val message = avviksprosentBeregnetJson(FØDSELSNUMMER, enAvviksvurdering)
        testRapid.sendTestMessage(message)
    }

    private fun avviksprosentBeregnetJson(
        fødselsnummer: String,
        avviksvurdering: AvviksvurderingFraSpleis
    ): String {
        @Language("JSON")
        val json = """
            {
              "@event_name": "avviksprosent_beregnet_event",
              "fødselsnummer": "$fødselsnummer",
              "skjæringstidspunkt": "${avviksvurdering.skjæringstidspunkt}",
              "vurderingstidspunkt": "2018-01-01T00:00:00.000",
              "vilkårsgrunnlagId": "${avviksvurdering.vilkårsgrunnlagId}",
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
            }
        """
        return json.trimIndent()
    }


    private fun avviksvurderingFraSpleisJson(
        aktørId: String,
        fødselsnummer: String,
        organisasjonsnummer: String,
        skjæringstidspunkt: LocalDate,
        vilkårsgrunnlagId: UUID
    ): String {
        @Language("JSON")
        val json = """
        {
          "@event_name": "avviksvurderinger",
          "fødselsnummer": "$fødselsnummer",
          "aktørId": "$aktørId",
          "skjæringstidspunkter": [
            {
              "skjæringstidspunkt": "$skjæringstidspunkt",
              "vurderingstidspunkt": "2018-01-01T00:00:00.000",
              "vilkårsgrunnlagId": "$vilkårsgrunnlagId",
              "avviksprosent": 0.0,
              "sammenligningsgrunnlagTotalbeløp": 50000.0,
              "beregningsgrunnlagTotalbeløp": 30000.0,
              "type": "SPLEIS",
              "omregnedeÅrsinntekter": [
                {
                  "orgnummer": "$organisasjonsnummer",
                  "beløp": 30000.0
                }
              ],
              "sammenligningsgrunnlag": [
                {
                  "orgnummer": "$organisasjonsnummer",
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
          ]
        }
    """.trimIndent()
        return json
    }

    private data class AvviksvurderingFraSpleis(
        val vilkårsgrunnlagId: UUID,
        val skjæringstidspunkt: LocalDate,
    )

    private fun flereAvviksvurderingerFraSpleisJson(
        aktørId: String,
        fødselsnummer: String,
        organisasjonsnummer: String,
        vararg avviksvurderinger: AvviksvurderingFraSpleis,
    ): String {
        val avviksvurderingerJson = avviksvurderinger.map {
            @Language("JSON")
            val json = """
                {
                  "skjæringstidspunkt": "${it.skjæringstidspunkt}",
                  "vurderingstidspunkt": "2018-01-01T00:00:00.000",
                  "vilkårsgrunnlagId": "${it.vilkårsgrunnlagId}",
                  "avviksprosent": 0.0,
                  "sammenligningsgrunnlagTotalbeløp": 50000.0,
                  "beregningsgrunnlagTotalbeløp": 30000.0,
                  "type": "SPLEIS",
                  "omregnedeÅrsinntekter": [
                    {
                      "orgnummer": "$organisasjonsnummer",
                      "beløp": 30000.0
                    }
                  ],
                  "sammenligningsgrunnlag": [
                    {
                      "orgnummer": "$organisasjonsnummer",
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
            json
        }.joinToString()

        @Language("JSON")
        val json = """
        {
          "@event_name": "avviksvurderinger",
          "fødselsnummer": "$fødselsnummer",
          "aktørId": "$aktørId",
          "skjæringstidspunkter": [
            $avviksvurderingerJson
          ]
        }
    """.trimIndent()
        return json
    }

    private fun avviksvurderingFraInfotrygdJson(
        aktørId: String,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): String {
        @Language("JSON")
        val json = """
        {
          "@event_name": "avviksvurderinger",
          "fødselsnummer": "$fødselsnummer",
          "aktørId": "$aktørId",
          "skjæringstidspunkter": [
            {
              "skjæringstidspunkt": "$skjæringstidspunkt",
              "vurderingstidspunkt": "2018-01-01T00:00:00.000",
              "vilkårsgrunnlagId": "291228ef-8b4e-4a1f-8065-ad7ed85acbb4",
              "type": "INFOTRYGD",
              "omregnedeÅrsinntekter": [],
              "sammenligningsgrunnlag": []
            }
          ]
        }
    """.trimIndent()
        return json
    }
}