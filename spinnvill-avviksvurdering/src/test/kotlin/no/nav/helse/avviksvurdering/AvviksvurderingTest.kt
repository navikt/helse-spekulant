package no.nav.helse.avviksvurdering

import no.nav.helse.InntektPerMåned
import no.nav.helse.KriterieObserver
import no.nav.helse.OmregnetÅrsinntekt
import no.nav.helse.Arbeidsgiverreferanse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.YearMonth

internal class AvviksvurderingTest {

    @Test
    fun `har gjort avviksvurdering før`() {
        val avviksvurdering = Avviksvurdering.nyAvviksvurdering(sammenligningsgrunnlag(50000.0))
        avviksvurdering.register(observer)

        avviksvurdering.håndter(beregningsgrunnlag("a1" to 600000.0))
        observer.clear()
        avviksvurdering.håndter(beregningsgrunnlag("a1" to 600000.0))
        assertEquals(0, observer.avviksvurderinger.size)
    }

    @Test
    fun `har ikke gjort avviksvurdering før og avvik innenfor akseptabelt avvik`() {
        val avviksvurdering = Avviksvurdering.nyAvviksvurdering(sammenligningsgrunnlag(50000.0))
        avviksvurdering.register(observer)

        avviksvurdering.håndter(beregningsgrunnlag("a1" to 600000.0))
        assertEquals(1, observer.avviksvurderinger.size)
        val (harAkseptabeltAvvik, avviksprosent) = observer.avviksvurderinger.single()
        assertEquals(true, harAkseptabeltAvvik)
        assertEquals(0.0, avviksprosent)
    }

    @Test
    fun `har ikke gjort avviksvurdering før og avvik utenfor akseptabelt avvik`() {
        val avviksvurdering = Avviksvurdering.nyAvviksvurdering(sammenligningsgrunnlag(50000.0))
        avviksvurdering.register(observer)

        avviksvurdering.håndter(beregningsgrunnlag("a1" to 360000.0))
        assertEquals(1, observer.avviksvurderinger.size)
        val (harAkseptabeltAvvik, avviksprosent) = observer.avviksvurderinger.single()
        assertEquals(false, harAkseptabeltAvvik)
        assertEquals(40.0, avviksprosent)
    }


    private fun sammenligningsgrunnlag(inntekt: Double) = Sammenligningsgrunnlag(
        listOf(
            ArbeidsgiverInntekt(Arbeidsgiverreferanse("a1"), List(12) { YearMonth.of(2018, it + 1) to InntektPerMåned(inntekt) }.toMap())
        )
    )

    private fun beregningsgrunnlag(vararg arbeidsgivere: Pair<String, Double>) =
        Beregningsgrunnlag.opprett(arbeidsgivere.toMap().entries.associate { Arbeidsgiverreferanse(it.key) to OmregnetÅrsinntekt(it.value) })

    private val observer = object : KriterieObserver {

        val avviksvurderinger = mutableListOf<Pair<Boolean, Double>>()

        fun clear() {
            avviksvurderinger.clear()
        }

        override fun avvikVurdert(harAkseptabeltAvvik: Boolean, avviksprosent: Double) {
            avviksvurderinger.add(harAkseptabeltAvvik to avviksprosent)
        }
    }
}

