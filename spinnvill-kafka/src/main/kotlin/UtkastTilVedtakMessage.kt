import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.convertValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import java.time.LocalDate
import java.util.*

class UtkastTilVedtakMessage(packet: JsonMessage) {

    // Litt usikker på om vi trenger denne, det føltes bare litt feil å fikle på det objektet vi får inn i konstruktøren
    private val result = objectMapper.readTree(packet.toJson()) as ObjectNode
    private lateinit var avviksvurderingId: UUID

    fun toJson(): JsonNode = result.deepCopy()

    fun leggTilAvviksvurderingId(id: UUID) {
        avviksvurderingId = id
    }

    @Suppress("removal")
    fun finalize(): Map<String, Any> =
        objectMapper.convertValue(result.apply {
            if (::avviksvurderingId.isInitialized) put("avviksvurderingId", avviksvurderingId.toString())
            else put("behandlingStartet", true)
        })

    val vilkårsgrunnlagId: UUID = packet["Godkjenning.vilkårsgrunnlagId"].asUUID()
    val skjæringstidspunkt: LocalDate = packet["Godkjenning.skjæringstidspunkt"].asLocalDate()
    val fødselsnummer: String = packet["fødselsnummer"].asText()
    val aktørId: String = packet["aktørId"].asText()
    val vedtaksperiodeId: UUID = packet["vedtaksperiodeId"].asUUID()
    val organisasjonsnummer: String = packet["organisasjonsnummer"].asText()
    val beregningsgrunnlag = packet["Godkjenning.omregnedeÅrsinntekter"].associate {
        it["organisasjonsnummer"].asText() to it["beløp"].asDouble()
    }
}
