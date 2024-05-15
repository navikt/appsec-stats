package no.nav.security

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class Slack(
    private val httpClient: HttpClient,
    private val slackWebhookUrl: String
) {

    suspend fun send(channel: String, heading: String, msg: String): SlackResponse {
        val toSend = Message(
            channel, listOf(
                markdownBlock(heading),
                dividerBlock(),
                markdownBlock(msg)
            )
        )

        return httpClient.post(slackWebhookUrl) {
            header(HttpHeaders.ContentType, Json)
            setBody(toSend)
        }.body()
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