package no.nav.security.mocks

import no.nav.security.kafka.KafkaConfig
import no.nav.security.kafka.KafkaProducer

class MockKafkaProducer : KafkaProducer(
    KafkaConfig(
        brokers = "localhost:9092",
        certificatePath = "/test",
        privateKeyPath = "/test",
        caPath = "/test",
        credstorePassword = "test",
        keystorePath = "/test",
        truststorePath = "/test",
        topic = "test-topic"
    )
) {
    var produceCalled = 0
    val producedMessages = mutableListOf<String>()
    
    override fun produce(message: String) {
        produceCalled++
        producedMessages.add(message)
    }
}
