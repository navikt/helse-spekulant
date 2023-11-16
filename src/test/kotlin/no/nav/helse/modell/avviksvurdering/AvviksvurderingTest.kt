package no.nav.helse.modell.avviksvurdering

import no.nav.helse.modell.KriterieObserver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AvviksvurderingTest {

    @Test
    fun `har gjort avviksvurdering før`() {
        val avviksvurdering = Avviksvurdering.nyAvviksvurdering()
        avviksvurdering.register(observer)

        avviksvurdering.håndter(omregnedeÅrsinntekter("a1" to 600000.0), sammenligningsgrunnlag(50000.0))
        observer.clear()
        avviksvurdering.håndter(omregnedeÅrsinntekter("a1" to 600000.0), sammenligningsgrunnlag(50000.0))
        assertEquals(0, observer.avviksvurderinger.size)
    }

    @Test
    fun `har ikke gjort avviksvurdering før og avvik innenfor akseptabelt avvik`() {
        val avviksvurdering = Avviksvurdering.nyAvviksvurdering()
        avviksvurdering.register(observer)

        avviksvurdering.håndter(omregnedeÅrsinntekter("a1" to 600000.0), sammenligningsgrunnlag(50000.0))
        assertEquals(1, observer.avviksvurderinger.size)
        val (harAkseptabeltAvvik, avviksprosent) = observer.avviksvurderinger.single()
        assertEquals(true, harAkseptabeltAvvik)
        assertEquals(0.0, avviksprosent)
    }

    @Test
    fun `har ikke gjort avviksvurdering før og avvik utenfor akseptabelt avvik`() {
        val avviksvurdering = Avviksvurdering.nyAvviksvurdering()
        avviksvurdering.register(observer)

        avviksvurdering.håndter(omregnedeÅrsinntekter("a1" to 360000.0), sammenligningsgrunnlag(50000.0))
        assertEquals(1, observer.avviksvurderinger.size)
        val (harAkseptabeltAvvik, avviksprosent) = observer.avviksvurderinger.single()
        assertEquals(false, harAkseptabeltAvvik)
        assertEquals(40.0, avviksprosent)
    }


    private fun sammenligningsgrunnlag(inntekt: Double) = Sammenligningsgrunnlag(
        listOf(
            ArbeidsgiverInntekt("a1", List(12) { inntekt })
        )
    )

    private fun omregnedeÅrsinntekter(vararg arbeidsgivere: Pair<String, Double>) =
        Beregningsgrunnlag(arbeidsgivere.toMap())

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

