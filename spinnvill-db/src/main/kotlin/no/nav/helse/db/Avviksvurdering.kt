package no.nav.helse.db

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class Avviksvurdering {
    private object Avviksvurderinger : UUIDTable(name = "avviksvurdering") {
        val fødselsnummer: Column<String> = varchar("fødselsnummer", 11)
        val skjæringstidspunkt: Column<LocalDate> = date("skjæringstidspunkt")
        val opprettet: Column<LocalDateTime> = datetime("opprettet").default(LocalDateTime.now())
    }

    internal class EnAvviksvurdering(id: EntityID<UUID>) : UUIDEntity(id) {
        companion object : UUIDEntityClass<EnAvviksvurdering>(Avviksvurderinger)

        var fødselsnummer by Avviksvurderinger.fødselsnummer
        var skjæringstidspunkt by Avviksvurderinger.skjæringstidspunkt
        var opprettet by Avviksvurderinger.opprettet
        val beregningsgrunnlag by EttBeregningsgrunnlag optionalBackReferencedOn Beregningsgrunnlag.avviksvurdering
        val sammenligningsgrunnlag by EttSammenligningsgrunnlag referrersOn Sammenligningsgrunnlag.avviksvurdering
    }

    private object Beregningsgrunnlag : UUIDTable() {
        val organisasjonsnummer: Column<String> = varchar("organisasjonsnummer", 9)
        val inntekt: Column<Double> = double("inntekt")
        val avviksvurdering = optReference("avviksvurdering_ref", Avviksvurderinger)
    }

    internal class EttBeregningsgrunnlag(id: EntityID<UUID>) : UUIDEntity(id) {
        companion object : UUIDEntityClass<EttBeregningsgrunnlag>(Beregningsgrunnlag)

        var organisasjonsnummer by Beregningsgrunnlag.organisasjonsnummer
        var inntekt by Beregningsgrunnlag.inntekt
        var avviksvurdering by EnAvviksvurdering optionalReferencedOn Beregningsgrunnlag.avviksvurdering
    }

    private object Sammenligningsgrunnlag : UUIDTable() {
        val organisasjonsnummer: Column<String> = varchar("organisasjonsnummer", 9)
        val avviksvurdering = reference("avviksvurdering_ref", Avviksvurderinger)
    }

    internal class EttSammenligningsgrunnlag(id: EntityID<UUID>) : UUIDEntity(id) {
        companion object : UUIDEntityClass<EttSammenligningsgrunnlag>(Sammenligningsgrunnlag)

        var organisasjonsnummer by Sammenligningsgrunnlag.organisasjonsnummer
        val inntekter by EnMånedsinntekt referrersOn Månedsinntekter.sammenligningsgrunnlag
        var avviksvurdering by EnAvviksvurdering referencedOn Sammenligningsgrunnlag.avviksvurdering
    }

    private object Månedsinntekter : UUIDTable(name = "manedsinntekt") {
        val inntekt: Column<Double> = double("inntekt")
        val år: Column<Int> = integer("år")
        val måned: Column<Int> = integer("måned")
        val sammenligningsgrunnlag = reference("sammenligningsgrunnlag_ref", Sammenligningsgrunnlag)
    }

    internal class EnMånedsinntekt(id: EntityID<UUID>) : UUIDEntity(id) {
        companion object : UUIDEntityClass<EnMånedsinntekt>(Månedsinntekter)
        var sammenligningsgrunnlag by EttSammenligningsgrunnlag referencedOn Månedsinntekter.sammenligningsgrunnlag
        var inntekt by Månedsinntekter.inntekt
        var år by Månedsinntekter.år
        var måned by Månedsinntekter.måned
    }

    fun upsert(
        fødselsnummer: Fødselsnummer,
        skjæringstidspunkt: LocalDate,
        sammenligningsgrunnlag: Map<Organisasjonsnummer, Map<InntektPerMåned, Pair<Måned, År>>>,
        beregningsgrunnlag: Map<Organisasjonsnummer, OmregnetÅrsinntekt>
    ): EnAvviksvurdering {
        return transaction {
            val enAvviksvurdering = EnAvviksvurdering.new {
                this.fødselsnummer = fødselsnummer.value
                this.skjæringstidspunkt = skjæringstidspunkt
            }

            sammenligningsgrunnlag.forEach { (organisasjonsnummer, inntekter) ->
                val ettSammenligningsgrunnlag = EttSammenligningsgrunnlag.new {
                    this.avviksvurdering = enAvviksvurdering
                    this.organisasjonsnummer = organisasjonsnummer.value
                }

                inntekter.forEach { (inntekt, månedPair) ->
                    EnMånedsinntekt.new {
                        this.sammenligningsgrunnlag = ettSammenligningsgrunnlag
                        this.inntekt = inntekt.value
                        this.måned = månedPair.first.value
                        this.år = månedPair.second.value
                    }
                }
            }

            beregningsgrunnlag.forEach { (organisasjonsnummer, inntekt) ->
                EttBeregningsgrunnlag.new {
                    this.organisasjonsnummer = organisasjonsnummer.value
                    this.inntekt = inntekt.value
                    this.avviksvurdering = enAvviksvurdering
                }
            }
            EnAvviksvurdering[enAvviksvurdering.id]
        }
    }

}

@JvmInline
value class Fødselsnummer(val value: String)

@JvmInline
value class Organisasjonsnummer(val value: String)

@JvmInline
value class InntektPerMåned(val value: Double)

@JvmInline
value class År(val value: Int)

@JvmInline
value class Måned(val value: Int)

@JvmInline
value class OmregnetÅrsinntekt(val value: Double)