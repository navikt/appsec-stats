package no.nav.security

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.ktor.http.ContentType.Application.Json

class Slack(
    private val httpClient: HttpClient,
    private val slackWebhookUrl: String
) {

    suspend fun send(msg: String) {
        val toSend = Message(
            "appsec-aktivitet", listOf(
                markdownBlock("GitHub Security Stats"),
                dividerBlock(),
                markdownBlock(msg)
            )
        )

        httpClient.post(slackWebhookUrl) {
            setBody(toSend)
        }.status.isSuccess()
    }

}

@Serializable
data class Message(val channel: String, val blocks: List<Block>)

@Serializable
data class Block(val type: String, val text: Text? = null)

@Serializable
data class Text(val type: String, val text: String)

@Serializable
data class SlackResponse(
    val ok: Boolean,
    @SerialName("error") val errorMessage: String?
)

private fun markdownBlock(txt: String) = Block(
    type = "section",
    text = Text(
        "mrkdwn",
        txt
    )
)

private fun dividerBlock() = Block(
    type = "divider"
)