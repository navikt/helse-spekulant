package no.nav.helse.mediator.producer

import no.nav.helse.Arbeidsgiverreferanse
import no.nav.helse.Beskrivelse
import no.nav.helse.Fordel
import no.nav.helse.InntektPerMåned
import no.nav.helse.avviksvurdering.ArbeidsgiverInntekt
import no.nav.helse.avviksvurdering.Beregningsgrunnlag
import no.nav.helse.avviksvurdering.Sammenligningsgrunnlag
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.*

internal class AvviksvurderingSubsumsjonBuilder(
    private val id: UUID,
    private val harAkseptabeltAvvik: Boolean,
    private val avviksprosent: Double,
    private val maksimaltTillattAvvik: Double,
    private val beregningsgrunnlag: Beregningsgrunnlag,
    sammenligningsgrunnlag: Sammenligningsgrunnlag,
) {
    private val sammenligningsgrunnlagBuilder = SammenligningsgrunnlagBuilder(sammenligningsgrunnlag)

    internal fun buildAvvikVurdert(): AvvikVurdertProducer.AvviksvurderingDto {
        val sammenligningsgrunnlagDto = sammenligningsgrunnlagBuilder.buildForAvviksvurdering()
        return AvvikVurdertProducer.AvviksvurderingDto(
            avviksprosent = avviksprosent,
            vurderingstidspunkt = LocalDateTime.now(),
            id = id,
            beregningsgrunnlagTotalbeløp = beregningsgrunnlag.totalOmregnetÅrsinntekt,
            omregnedeÅrsinntekter = beregningsgrunnlag.omregnedeÅrsinntekter,
            sammenligningsgrunnlagTotalbeløp = sammenligningsgrunnlagDto.totalbeløp,
            innrapporterteInntekter = sammenligningsgrunnlagDto.arbeidsgiverligeInntekter
        )
    }

    internal fun `8-30 ledd 1`(): SubsumsjonProducer.SubsumsjonsmeldingDto {
        return SubsumsjonProducer.SubsumsjonsmeldingDto(
            paragraf = "8-30",
            ledd = 1,
            bokstav = null,
            punktum = null,
            lovverk = "folketrygdloven",
            lovverksversjon = LocalDate.of(2019, 1, 1),
            utfall = SubsumsjonProducer.Utfall.VILKAR_BEREGNET,
            input = mapOf(
                "omregnedeÅrsinntekter" to beregningsgrunnlag.omregnedeÅrsinntekter.map { (arbeidsgiverreferanse, omregnetÅrsinntekt) ->
                    mapOf(
                        "arbeidsgiverreferanse" to arbeidsgiverreferanse.value,
                        "inntekt" to omregnetÅrsinntekt.value
                    )
                },
            ),
            output = mapOf(
                "grunnlagForSykepengegrunnlag" to beregningsgrunnlag.totalOmregnetÅrsinntekt,
            )
        )
    }

    internal fun `8-30 ledd 2 punktum 1`(): SubsumsjonProducer.SubsumsjonsmeldingDto {
        val sammenligningsgrunnlagDto = sammenligningsgrunnlagBuilder.buildForSubsumsjon()
        return SubsumsjonProducer.SubsumsjonsmeldingDto(
            paragraf = "8-30",
            ledd = 2,
            bokstav = null,
            punktum = 1,
            lovverk = "folketrygdloven",
            lovverksversjon = LocalDate.of(2019, 1, 1),
            utfall = SubsumsjonProducer.Utfall.VILKAR_BEREGNET,
            input = mapOf(
                "maksimaltTillattAvvikPåÅrsinntekt" to maksimaltTillattAvvik,
                "grunnlagForSykepengegrunnlag" to mapOf(
                    "totalbeløp" to beregningsgrunnlag.totalOmregnetÅrsinntekt,
                    "omregnedeÅrsinntekter" to beregningsgrunnlag.omregnedeÅrsinntekter.map { (arbeidsgiverreferanse, omregnetÅrsinntekt) ->
                        mapOf(
                            "arbeidsgiverreferanse" to arbeidsgiverreferanse.value,
                            "inntekt" to omregnetÅrsinntekt.value
                        )
                    }
                ),
                "sammenligningsgrunnlag" to mapOf(
                    "totalbeløp" to sammenligningsgrunnlagDto.totalbeløp,
                    "innrapporterteMånedsinntekter" to sammenligningsgrunnlagDto.månedligeInntekter.map { (måned, inntekter) ->
                        mapOf(
                            "måned" to måned,
                            "inntekter" to inntekter.map {
                                mapOf(
                                    "arbeidsgiverreferanse" to it.arbeidsgiverreferanse.value,
                                    "inntekt" to it.inntekt.value,
                                    "fordel" to it.fordel?.value,
                                    "beskrivelse" to it.beskrivelse?.value,
                                    "inntektstype" to it.inntektstype,
                                )
                            }
                        )
                    }
                )
            ),
            output = mapOf(
                "avviksprosent" to avviksprosent,
                "harAkseptabeltAvvik" to harAkseptabeltAvvik
            )
        )
    }

    private data class SammenligningsgrunnlagSubsumsjonDto(
        val totalbeløp: Double,
        val månedligeInntekter: Map<YearMonth, List<MånedligInntekt>>
    )

    private data class SammenligningsgrunnlagAvviksvurderingDto(
        val totalbeløp: Double,
        val arbeidsgiverligeInntekter: List<AvvikVurdertProducer.AvviksvurderingDto.InnrapportertInntektDto>
    )

    private data class MånedligInntekt(
        val arbeidsgiverreferanse: Arbeidsgiverreferanse,
        val inntekt: InntektPerMåned,
        val fordel: Fordel?,
        val beskrivelse: Beskrivelse?,
        val inntektstype: String
    )

    private class SammenligningsgrunnlagBuilder(private val sammenligningsgrunnlag: Sammenligningsgrunnlag) {
        private var totalbeløp  = sammenligningsgrunnlag.totaltInnrapportertÅrsinntekt
        private val arbeidsgiverInntekter = sammenligningsgrunnlag.inntekter

        fun buildForSubsumsjon(): SammenligningsgrunnlagSubsumsjonDto {
            return SammenligningsgrunnlagSubsumsjonDto(
                totalbeløp = totalbeløp,
                månedligeInntekter = sammenligningsgrunnlag.inntekter
                    .flatMap { (arbeidsgiverreferanse, inntekter) ->
                        inntekter.map { inntekt ->
                            inntekt.måned to MånedligInntekt(
                                arbeidsgiverreferanse = arbeidsgiverreferanse,
                                inntekt = inntekt.inntekt,
                                fordel = inntekt.fordel,
                                beskrivelse = inntekt.beskrivelse,
                                inntektstype = inntekt.inntektstype.toSubsumsjonString()
                            )
                        }
                    }
                    .groupBy({ it.first }) { it.second }
            )
        }

        fun buildForAvviksvurdering(): SammenligningsgrunnlagAvviksvurderingDto {
            return SammenligningsgrunnlagAvviksvurderingDto(
                totalbeløp = totalbeløp,
                arbeidsgiverligeInntekter = arbeidsgiverInntekter.map { (arbeidsgiverreferanse, inntekter) ->
                    AvvikVurdertProducer.AvviksvurderingDto.InnrapportertInntektDto(
                        arbeidsgiverreferanse,
                        inntekter.map {
                            AvvikVurdertProducer.AvviksvurderingDto.MånedligInntektDto(
                                it.måned,
                                it.inntekt
                            )
                        }
                    )
                }
            )
        }

        fun ArbeidsgiverInntekt.Inntektstype.toSubsumsjonString() = when (this) {
            ArbeidsgiverInntekt.Inntektstype.LØNNSINNTEKT -> "LØNNSINNTEKT"
            ArbeidsgiverInntekt.Inntektstype.NÆRINGSINNTEKT -> "NÆRINGSINNTEKT"
            ArbeidsgiverInntekt.Inntektstype.PENSJON_ELLER_TRYGD -> "PENSJON_ELLER_TRYGD"
            ArbeidsgiverInntekt.Inntektstype.YTELSE_FRA_OFFENTLIGE -> "YTELSE_FRA_OFFENTLIGE"
        }
    }
}
