package no.nav.helse

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.db.Database
import no.nav.helse.mediator.Mediator
import no.nav.helse.rapids_rivers.RapidApplication

internal fun main() {
    App().start()
}

class App(private val env: Map<String, String> = System.getenv()) : RapidsConnection.StatusListener {
    private lateinit var database: Database
    private val rapidsConnection = RapidApplication.create(env)

    init {
        rapidsConnection.register(this)
        Mediator(
            versjonAvKode = VersjonAvKode(versjonAvKode(env)),
            rapidsConnection = rapidsConnection,
            databaseProvider = { database }
        )
    }

    internal fun start() {
        rapidsConnection.start()
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        database = Database.instance(env)
        database.migrate()
    }

    private fun versjonAvKode(env: Map<String, String>): String {
        return env["NAIS_APP_IMAGE"] ?: throw IllegalArgumentException("NAIS_APP_IMAGE env variable is missing")
    }
}
