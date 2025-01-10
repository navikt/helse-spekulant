package no.nav.helse.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asYearMonth
import no.nav.helse.Beskrivelse
import no.nav.helse.Fordel
import java.time.LocalDate
import java.time.YearMonth

class SammenligningsgrunnlagMessage(packet: JsonMessage) {
    val utkastTilVedtakJson: String = objectMapper.writeValueAsString(packet["utkastTilVedtak"])
    val skjæringstidspunkt: LocalDate = packet["InntekterForSammenligningsgrunnlag.skjæringstidspunkt"].asLocalDate()
    val fødselsnummer: String = packet["fødselsnummer"].asText()
    val sammenligningsgrunnlag: Map<String, List<Inntekt>> = mapSammenligningsgrunnlag(packet["@løsning.InntekterForSammenligningsgrunnlag"])

    data class Inntekt(
        val beløp: Double,
        val inntektstype: Inntektstype,
        val fordel: Fordel?,
        val beskrivelse: Beskrivelse?,
        val årMåned: YearMonth
    )

    enum class Inntektstype {
        LØNNSINNTEKT,
        NÆRINGSINNTEKT,
        PENSJON_ELLER_TRYGD,
        YTELSE_FRA_OFFENTLIGE,
    }

    private fun JsonNode.asInntektstype() = when (this.asText()) {
        "LOENNSINNTEKT" -> Inntektstype.LØNNSINNTEKT
        "NAERINGSINNTEKT" -> Inntektstype.NÆRINGSINNTEKT
        "PENSJON_ELLER_TRYGD" -> Inntektstype.PENSJON_ELLER_TRYGD
        "YTELSE_FRA_OFFENTLIGE" -> Inntektstype.YTELSE_FRA_OFFENTLIGE
        else -> error("Kunne ikke mappe Inntektstype")
    }

    private fun JsonNode.arbeidsgiver() = when {
        path("orgnummer").isTextual -> path("orgnummer").asText()
        path("fødselsnummer").isTextual -> path("fødselsnummer").asText()
        else -> error("Mangler arbeidsgiver for inntekt i svar på sammenligningsgrunnlagbehov")
    }

    private fun mapSammenligningsgrunnlag(opplysninger: JsonNode) =
        opplysninger
            .flatMap { måned ->
                måned["inntektsliste"].map { opplysning ->
                    (opplysning as ObjectNode).put("årMåned", måned.path("årMåned").asText())
                }
            }
            .groupBy({ inntekt -> inntekt.arbeidsgiver() }) { inntekt ->
                Inntekt(
                    årMåned = inntekt["årMåned"].asYearMonth(),
                    beløp = inntekt["beløp"].asDouble(),
                    inntektstype = inntekt["inntektstype"].asInntektstype(),
                    fordel = if (inntekt.path("fordel").isTextual) Fordel(inntekt["fordel"].asText()) else null,
                    beskrivelse = if (inntekt.path("beskrivelse").isTextual) Beskrivelse(inntekt["beskrivelse"].asText()) else null
                )
            }
}
