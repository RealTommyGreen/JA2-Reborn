package com.ja2.reborn

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Ja2Json(
    @SerialName("game_dir")
    val vanillaGameDir: String? = null,
    @SerialName("resversion")
    val vanillaGameVersion: VanillaVersion? = null,
    @SerialName("save_game_dir")
    val saveGameDir: String? = null,
    @SerialName("externalized_json_dir")
    val externalizedJsonDir: String? = null,
    @SerialName("res")
    val resolution: Resolution? = null,
    @SerialName("resolution_mode")
    val resolutionMode: ResolutionMode? = null,
    @SerialName("scaling")
    val scalingQuality: ScalingQuality? = null,
    @SerialName("mouse_mode")
    val mouseMode: MouseMode? = null,
    @SerialName("expert_settings")
    val expertSettings: Boolean? = null,
    @SerialName("debug")
    val debug: Boolean? = null
)
