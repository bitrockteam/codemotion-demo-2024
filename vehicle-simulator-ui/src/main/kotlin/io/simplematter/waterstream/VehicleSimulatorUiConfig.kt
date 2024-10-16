package io.simplematter.waterstream

import com.typesafe.config.ConfigFactory
import io.github.config4k.extract


data class VehicleSimulatorUiConfig(
    val messageCountPanelAddress: String,
    val mqttHost: String,
    val mqttPort: String,
    val mqttUseSsl: String,
    val mqttClientPrefix: String,
    val mqttVehiclesTopicPrefix: String,
    val mqttDirectionStatsTopicPrefix: String,
    val mapboxToken: String,
    val querySQLServiceUrl: String,
    val queryDocumentServiceUrl: String
) {
    companion object {
        fun load(): VehicleSimulatorUiConfig {
            val config = ConfigFactory.load()
            return config.extract<VehicleSimulatorUiConfig>()
        }
    }
}