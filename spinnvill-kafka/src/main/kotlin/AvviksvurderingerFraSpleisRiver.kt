import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

class AvviksvurderingerFraSpleisRiver(
    rapidsConnection: RapidsConnection,
    private val messageHandler: MessageHandler
): River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "avviksvurderinger")
                it.requireKey("fødselsnummer")
                it.requireArray("skjæringstidspunkter") {
                    requireKey(
                        "skjæringstidspunkt", "vurderingstidspunkt", "type", "vilkårsgrunnlagId",
                    )
                    interestedIn("avviksprosent", "beregningsgrunnlagTotalbeløp", "sammenligningsgrunnlagTotalbeløp")
                    requireArray("omregnedeÅrsinntekter") {
                        requireKey("orgnummer", "beløp")
                    }
                    requireArray("sammenligningsgrunnlag") {
                        requireKey("orgnummer")
                        requireArray("skatteopplysninger") {
                            requireKey("beløp", "måned", "type", "fordel", "beskrivelse")
                        }
                    }
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        messageHandler.håndter(AvviksvurderingerFraSpleisMessage(packet))
    }
}