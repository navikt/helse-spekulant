import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

class AvviksvurderingFraSpleisRiver(
    rapidsConnection: RapidsConnection,
    private val messageHandler: MessageHandler
): River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "avviksprosent_beregnet_event")
                it.requireKey("fødselsnummer")
                it.requireKey("skjæringstidspunkt", "vurderingstidspunkt", "vilkårsgrunnlagId")
                it.requireKey("avviksprosent", "beregningsgrunnlagTotalbeløp", "sammenligningsgrunnlagTotalbeløp")
                it.requireArray("omregnedeÅrsinntekter") {
                    requireKey("orgnummer", "beløp")
                }
                it.requireArray("sammenligningsgrunnlag") {
                    requireKey("orgnummer")
                    requireArray("skatteopplysninger") {
                        requireKey("beløp", "måned", "type", "fordel", "beskrivelse")
                    }
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        messageHandler.håndter(EnAvviksvurderingFraSpleisMessage(packet))
    }
}