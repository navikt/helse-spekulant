package no.nav.helse.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.*
import no.nav.helse.avviksvurdering.*
import no.nav.helse.db.Database
import no.nav.helse.dto.AvviksvurderingDto
import no.nav.helse.kafka.*
import no.nav.helse.mediator.producer.*
import org.slf4j.LoggerFactory
import java.time.LocalDate

class Mediator(
    private val versjonAvKode: VersjonAvKode,
    private val rapidsConnection: RapidsConnection,
    databaseProvider: () -> Database,
) : MessageHandler {

    private val database by lazy(databaseProvider)

    init {
        GodkjenningsbehovRiver(rapidsConnection, this)
        SammenligningsgrunnlagRiver(rapidsConnection, this)
    }

    override fun håndter(message: GodkjenningsbehovMessage) {
        val meldingProducer = nyMeldingProducer(message)

        logg.info("Behandler utkast_til_vedtak for {}", kv("vedtaksperiodeId", message.vedtaksperiodeId))
        sikkerlogg.info("Behandler utkast_til_vedtak for {}, {}", kv("fødselsnummer", message.fødselsnummer), kv("vedtaksperiodeId", message.vedtaksperiodeId))

        val behovProducer = BehovProducer(utkastTilVedtakJson = message.toJson())
        val varselProducer = VarselProducer(vedtaksperiodeId = message.vedtaksperiodeId)
        val subsumsjonProducer = nySubsumsjonProducer(message)
        val avvikVurdertProducer = AvvikVurdertProducer(vilkårsgrunnlagId = message.vilkårsgrunnlagId)
        val godkjenningsbehovProducer = GodkjenningsbehovProducer(message)

        meldingProducer.nyProducer(behovProducer, varselProducer, subsumsjonProducer, avvikVurdertProducer, godkjenningsbehovProducer)

        val beregningsgrunnlag = nyttBeregningsgrunnlag(message)
        val avviksvurderinger = hentGrunnlagshistorikk(Fødselsnummer(message.fødselsnummer), message.skjæringstidspunkt)

        when (val resultat = avviksvurderinger.håndterNytt(beregningsgrunnlag)) {
            is Avviksvurderingsresultat.TrengerSammenligningsgrunnlag -> behovProducer.sammenligningsgrunnlag(resultat.behov)
            is Avviksvurderingsresultat.AvvikVurdert -> {
                val vurdertAvvik = resultat.vurdering
                godkjenningsbehovProducer.registrerGodkjenningsbehovForUtsending(resultat.grunnlag)
                subsumsjonProducer.avvikVurdert(vurdertAvvik)
                avvikVurdertProducer.avvikVurdert(vurdertAvvik)
                varselProducer.avvikVurdert(vurdertAvvik.harAkseptabeltAvvik, vurdertAvvik.avviksprosent)
            }
            is Avviksvurderingsresultat.TrengerIkkeNyVurdering -> {
                godkjenningsbehovProducer.registrerGodkjenningsbehovForUtsending(resultat.gjeldendeGrunnlag)
            }
        }
        avviksvurderinger.lagre()
        meldingProducer.publiserMeldinger()
    }

    override fun håndter(sammenligningsgrunnlagMessage: SammenligningsgrunnlagMessage) {
        val fødselsnummer = Fødselsnummer(sammenligningsgrunnlagMessage.fødselsnummer)
        val skjæringstidspunkt = sammenligningsgrunnlagMessage.skjæringstidspunkt

        if (finnAvviksvurderingsgrunnlag(fødselsnummer, skjæringstidspunkt) != null) {
            logg.warn("Ignorerer duplikat sammenligningsgrunnlag for eksisterende avviksvurdering")
            sikkerlogg.warn("Ignorerer duplikat sammenligningsgrunnlag for {} {}", kv("fødselsnummer", fødselsnummer.value), kv("skjæringstidspunkt", skjæringstidspunkt))
            return
        }

        val avviksvurderinger = hentGrunnlagshistorikk(fødselsnummer, skjæringstidspunkt)
        val sammenligningsgrunnlag = nyttSammenligningsgrunnlag(sammenligningsgrunnlagMessage)
        avviksvurderinger.håndterNytt(sammenligningsgrunnlag)
        avviksvurderinger.lagre()

//        rapidsConnection.queueReplayMessage(fødselsnummer.value, sammenligningsgrunnlagMessage.utkastTilVedtakJson)
    }

    private fun Grunnlagshistorikk.lagre() {
        val builder = DatabaseDtoBuilder()
        this.accept(builder)
        database.lagreGrunnlagshistorikk(builder.buildAll())
    }

    private fun finnAvviksvurderingsgrunnlag(fødselsnummer: Fødselsnummer, skjæringstidspunkt: LocalDate): Avviksvurderingsgrunnlag? {
        return database.finnSisteAvviksvurderingsgrunnlag(fødselsnummer, skjæringstidspunkt)?.tilDomene()
    }

    private fun hentGrunnlagshistorikk(fødselsnummer: Fødselsnummer, skjæringstidspunkt: LocalDate): Grunnlagshistorikk {
        val grunnlag = database.finnAvviksvurderingsgrunnlag(fødselsnummer, skjæringstidspunkt).map { it.tilDomene() }
        return Grunnlagshistorikk(fødselsnummer, skjæringstidspunkt, grunnlag)
    }

    private fun nyMeldingProducer(godkjenningsbehovMessage: GodkjenningsbehovMessage) = MeldingProducer(
        fødselsnummer = godkjenningsbehovMessage.fødselsnummer.somFnr(),
        organisasjonsnummer = godkjenningsbehovMessage.organisasjonsnummer.somArbeidsgiverref(),
        skjæringstidspunkt = godkjenningsbehovMessage.skjæringstidspunkt,
        vedtaksperiodeId = godkjenningsbehovMessage.vedtaksperiodeId,
        rapidsConnection = rapidsConnection
    )

    private fun nySubsumsjonProducer(godkjenningsbehovMessage: GodkjenningsbehovMessage): SubsumsjonProducer {
        return SubsumsjonProducer(
            fødselsnummer = godkjenningsbehovMessage.fødselsnummer.somFnr(),
            vedtaksperiodeId = godkjenningsbehovMessage.vedtaksperiodeId,
            organisasjonsnummer = godkjenningsbehovMessage.organisasjonsnummer.somArbeidsgiverref(),
            vilkårsgrunnlagId = godkjenningsbehovMessage.vilkårsgrunnlagId,
            versjonAvKode = versjonAvKode
        )
    }

    private fun nyttBeregningsgrunnlag(godkjenningsbehovMessage: GodkjenningsbehovMessage): Beregningsgrunnlag {
        return Beregningsgrunnlag.opprett(
            godkjenningsbehovMessage.beregningsgrunnlag.entries.associate {
                Arbeidsgiverreferanse(it.key) to OmregnetÅrsinntekt(it.value)
            }
        )
    }

    private fun nyttSammenligningsgrunnlag(sammenligningsgrunnlagMessage: SammenligningsgrunnlagMessage): Sammenligningsgrunnlag {
        return Sammenligningsgrunnlag(
            sammenligningsgrunnlagMessage.sammenligningsgrunnlag.map {(arbeidsgiver, inntekter) ->
                ArbeidsgiverInntekt(arbeidsgiver.somArbeidsgiverref(), inntekter.map {
                    ArbeidsgiverInntekt.MånedligInntekt(
                        inntekt = InntektPerMåned(it.beløp),
                        måned = it.årMåned,
                        fordel = it.fordel,
                        beskrivelse = it.beskrivelse,
                        inntektstype = it.inntektstype.tilDomene()
                    )
                })
            }
        )
    }

    internal companion object {
        private val logg = LoggerFactory.getLogger(Mediator::class.java)
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")


        internal fun AvviksvurderingDto.tilDomene(): Avviksvurderingsgrunnlag {
            val beregningsgrunnlag = beregningsgrunnlag?.let {
                Beregningsgrunnlag.opprett(it.omregnedeÅrsinntekter)
            } ?: Ingen

            return Avviksvurderingsgrunnlag(
                id = id,
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                beregningsgrunnlag = beregningsgrunnlag,
                opprettet = opprettet,
                kilde = this.kilde.tilDomene(),
                sammenligningsgrunnlag = Sammenligningsgrunnlag(
                    sammenligningsgrunnlag.innrapporterteInntekter.map { (organisasjonsnummer, inntekter) ->
                        ArbeidsgiverInntekt(
                            arbeidsgiverreferanse = organisasjonsnummer,
                            inntekter = inntekter.map {
                                ArbeidsgiverInntekt.MånedligInntekt(
                                    inntekt = it.inntekt,
                                    måned = it.måned,
                                    fordel = it.fordel,
                                    beskrivelse = it.beskrivelse,
                                    inntektstype = it.inntektstype.tilDomene()
                                )
                            }
                        )
                    }
                )
            )
        }

        private fun SammenligningsgrunnlagMessage.Inntektstype.tilDomene(): ArbeidsgiverInntekt.Inntektstype {
            return when (this) {
                SammenligningsgrunnlagMessage.Inntektstype.LØNNSINNTEKT -> ArbeidsgiverInntekt.Inntektstype.LØNNSINNTEKT
                SammenligningsgrunnlagMessage.Inntektstype.NÆRINGSINNTEKT -> ArbeidsgiverInntekt.Inntektstype.NÆRINGSINNTEKT
                SammenligningsgrunnlagMessage.Inntektstype.PENSJON_ELLER_TRYGD -> ArbeidsgiverInntekt.Inntektstype.PENSJON_ELLER_TRYGD
                SammenligningsgrunnlagMessage.Inntektstype.YTELSE_FRA_OFFENTLIGE -> ArbeidsgiverInntekt.Inntektstype.YTELSE_FRA_OFFENTLIGE
            }
        }

        private fun AvviksvurderingDto.InntektstypeDto.tilDomene(): ArbeidsgiverInntekt.Inntektstype {
            return when (this) {
                AvviksvurderingDto.InntektstypeDto.LØNNSINNTEKT -> ArbeidsgiverInntekt.Inntektstype.LØNNSINNTEKT
                AvviksvurderingDto.InntektstypeDto.NÆRINGSINNTEKT -> ArbeidsgiverInntekt.Inntektstype.NÆRINGSINNTEKT
                AvviksvurderingDto.InntektstypeDto.PENSJON_ELLER_TRYGD -> ArbeidsgiverInntekt.Inntektstype.PENSJON_ELLER_TRYGD
                AvviksvurderingDto.InntektstypeDto.YTELSE_FRA_OFFENTLIGE -> ArbeidsgiverInntekt.Inntektstype.YTELSE_FRA_OFFENTLIGE
            }
        }

        private fun AvviksvurderingDto.KildeDto.tilDomene() = when (this) {
            AvviksvurderingDto.KildeDto.SPINNVILL -> Kilde.SPINNVILL
            AvviksvurderingDto.KildeDto.SPLEIS -> Kilde.SPLEIS
            AvviksvurderingDto.KildeDto.INFOTRYGD -> Kilde.INFOTRYGD
        }
    }
}
