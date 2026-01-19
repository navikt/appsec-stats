package no.nav.security.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.util.Properties

class KafkaProducer(
    protected val kafkaConfig: KafkaConfig,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(KafkaProducer::class.java)
    private val producer: Producer<String, String> by lazy { createProducer() }

    fun produce(message: String, key: String? = null) {
        try {
            val record = ProducerRecord(kafkaConfig.topic, key, message)
            val metadata = producer.send(record).get()
            logger.info(
                "Message produced successfully to topic: {}, partition: {}, offset: {}",
                metadata.topic(),
                metadata.partition(),
                metadata.offset()
            )
        } catch (e: Exception) {
            logger.error("Failed to produce message to topic ${kafkaConfig.topic}", e)
            throw e
        }
    }

    private fun createProducer(): Producer<String, String> {
        val props = Properties().apply {
            // Basic producer configuration
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.brokers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.RETRIES_CONFIG, 3)
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
            put(ProducerConfig.LINGER_MS_CONFIG, 10)
            put(ProducerConfig.BATCH_SIZE_CONFIG, 16384)

            // Security configuration
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
            put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, kafkaConfig.keystorePath)
            put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, kafkaConfig.credstorePassword)
            put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, kafkaConfig.truststorePath)
            put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, kafkaConfig.credstorePassword)
            put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, kafkaConfig.credstorePassword)
        }

        logger.info("Creating Kafka producer for topic: {} with brokers: {}", kafkaConfig.topic, kafkaConfig.brokers)
        return org.apache.kafka.clients.producer.KafkaProducer(props)
    }

    override fun close() {
        try {
            logger.info("Flushing and closing Kafka producer for topic: {}", kafkaConfig.topic)
            producer.flush() // Ensure all buffered messages are sent before closing
            producer.close()
        } catch (e: Exception) {
            logger.error("Error closing Kafka producer", e)
        }
    }
}