import no.nav.helse.Arbeidsgiverreferanse
import no.nav.helse.OmregnetÅrsinntekt
import Avviksvurderingkilde.SPLEIS
import no.nav.helse.rapids_rivers.*
import no.nav.helse.somArbeidsgiverref
import no.nav.helse.somFnr
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.*

class AvviksvurderingerFraSpleisMessage(packet: JsonMessage) {
    val fødselsnummer = packet["fødselsnummer"].asText().somFnr()
    val avviksvurderinger: List<AvviksvurderingFraSpleis> = packet["skjæringstidspunkter"].map { avviksvurdering ->
        AvviksvurderingFraSpleis(
            skjæringstidspunkt = avviksvurdering["skjæringstidspunkt"].asLocalDate(),
            vurderingstidspunkt = avviksvurdering["vurderingstidspunkt"].asLocalDateTime(),
            kilde = enumValueOf(avviksvurdering["type"].asText()),
            id = UUID.randomUUID(),
            vilkårsgrunnlagId = avviksvurdering["vilkårsgrunnlagId"].asUUID(),
            avviksprosent = avviksvurdering["avviksprosent"]?.asDouble(),
            beregningsgrunnlagTotalbeløp = avviksvurdering["beregningsgrunnlagTotalbeløp"]?.asDouble(),
            sammenligningsgrunnlagTotalbeløp = avviksvurdering["sammenligningsgrunnlagTotalbeløp"]?.asDouble(),
            omregnedeÅrsinntekter = avviksvurdering["omregnedeÅrsinntekter"]?.associate {
                it["orgnummer"].asText().somArbeidsgiverref() to OmregnetÅrsinntekt(it["beløp"].asDouble())
            } ?: emptyMap(),
            innrapporterteInntekter = avviksvurdering["sammenligningsgrunnlag"]?.map { sammenligningsgrunnlag ->
                InnrapportertInntektFraSpleis(
                    orgnummer = sammenligningsgrunnlag["orgnummer"].asText(),
                    inntekter = sammenligningsgrunnlag["skatteopplysninger"].map {
                        MånedligInntektFraSpleis(
                            beløp = it["beløp"].asDouble(),
                            måned = it["måned"].asYearMonth(),
                            type = it["type"].asText(),
                            fordel = it["fordel"].asText(),
                            beskrivelse = it["beskrivelse"].asText(),
                        )
                    }
                )
            } ?: emptyList()
        )
    }
}

class EnAvviksvurderingFraSpleisMessage(packet: JsonMessage) {
    val fødselsnummer = packet["fødselsnummer"].asText().somFnr()
    val avviksvurdering = AvviksvurderingFraSpleis(
        skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate(),
        vurderingstidspunkt = packet["vurderingstidspunkt"].asLocalDateTime(),
        kilde = SPLEIS,
        id = UUID.randomUUID(),
        vilkårsgrunnlagId = packet["vilkårsgrunnlagId"].asUUID(),
        avviksprosent = packet["avviksprosent"].asDouble(),
        beregningsgrunnlagTotalbeløp = packet["beregningsgrunnlagTotalbeløp"].asDouble(),
        sammenligningsgrunnlagTotalbeløp = packet["sammenligningsgrunnlagTotalbeløp"].asDouble(),
        omregnedeÅrsinntekter = packet["omregnedeÅrsinntekter"].associate {
            it["orgnummer"].asText().somArbeidsgiverref() to OmregnetÅrsinntekt(it["beløp"].asDouble())
        },
        innrapporterteInntekter = packet["sammenligningsgrunnlag"].map { sammenligningsgrunnlag ->
            InnrapportertInntektFraSpleis(
                orgnummer = sammenligningsgrunnlag["orgnummer"].asText(),
                inntekter = sammenligningsgrunnlag["skatteopplysninger"].map {
                    MånedligInntektFraSpleis(
                        beløp = it["beløp"].asDouble(),
                        måned = it["måned"].asYearMonth(),
                        type = it["type"].asText(),
                        fordel = it["fordel"].asText(),
                        beskrivelse = it["beskrivelse"].asText(),
                    )
                }
            )
        }
    )
}

data class AvviksvurderingFraSpleis(
    val skjæringstidspunkt: LocalDate,
    val vurderingstidspunkt: LocalDateTime,
    val kilde: Avviksvurderingkilde,
    val id: UUID,
    val vilkårsgrunnlagId: UUID,
    val avviksprosent: Double?,
    val beregningsgrunnlagTotalbeløp: Double?,
    val sammenligningsgrunnlagTotalbeløp: Double?,
    val omregnedeÅrsinntekter: Map<Arbeidsgiverreferanse, OmregnetÅrsinntekt>,
    val innrapporterteInntekter: List<InnrapportertInntektFraSpleis>
)

data class InnrapportertInntektFraSpleis(
    val orgnummer: String,
    val inntekter: List<MånedligInntektFraSpleis>
)

data class MånedligInntektFraSpleis(
    val beløp: Double,
    val måned: YearMonth,
    val type: String,
    val fordel: String,
    val beskrivelse: String,
)

enum class Avviksvurderingkilde {
    SPLEIS,
    INFOTRYGD
}