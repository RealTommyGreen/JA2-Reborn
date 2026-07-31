package com.ja2.reborn

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

enum class VanillaVersion(val value: String) {
    DUTCH("DUTCH"),
    ENGLISH("ENGLISH"),
    FRENCH("FRENCH"),
    GERMAN("GERMAN"),
    ITALIAN("ITALIAN"),
    POLISH("POLISH"),
    RUSSIAN("RUSSIAN"),
    RUSSIAN_GOLD("RUSSIAN_GOLD"),
    SIMPLIFIED_CHINESE("SIMPLIFIED_CHINESE");

    companion object {
        val DEFAULT = ENGLISH
    }
}

@Serializable(with = ResolutionSerializer::class)
class Resolution(
    val width: UInt,
    val height: UInt
) {
    companion object {
        val DEFAULT = Resolution(640u, 480u)
    }
}


object ResolutionSerializer : KSerializer<Resolution> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Resolution", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Resolution) {
        val width = value.width.toString()
        val height = value.height.toString()
        encoder.encodeString("${width}x${height}")
    }

    override fun deserialize(decoder: Decoder): Resolution {
        val parts = decoder.decodeString().split("x")
        if (parts.size != 2) {
            throw SerializationException("must be in format 640x480")
        }
        val width: UInt
        val height: UInt
        try {
            width = parts[0].toUInt()
            height = parts[1].toUInt()
        } catch (e: NumberFormatException) {
            throw SerializationException("must be in format 640x480")
        }
        return Resolution(width, height)
    }
}

enum class ScalingQuality(val value: String) {
    LINEAR("LINEAR"),
    NEAR_PERFECT("NEAR_PERFECT"),
    PERFECT("PERFECT");

    companion object {
        val DEFAULT = NEAR_PERFECT
    }
}

enum class MouseMode(val value: String) {
    @SerialName("touchpad")
    TOUCHPAD("touchpad"),
    @SerialName("absolute")
    ABSOLUTE("absolute"),
    @SerialName("touchscreen")
    TOUCHSCREEN("touchscreen"),
    @SerialName("hardware")
    HARDWARE("hardware");

    companion object {
        val DEFAULT = TOUCHPAD
        val DISPLAY_ORDER = arrayOf(TOUCHPAD, HARDWARE, ABSOLUTE, TOUCHSCREEN)
    }
}

class ConfigurationModel : ViewModel() {

    val vanillaGameDir = MutableLiveData<String?>()
    val vanillaGameVersion = MutableLiveData(VanillaVersion.DEFAULT)
    val saveGameDir = MutableLiveData<String?>()
    val externalizedJsonDir = MutableLiveData<String?>()
    val resolutionMode = MutableLiveData(ResolutionMode.DEFAULT)
    val resolution = MutableLiveData(Resolution.DEFAULT)
    val scalingQuality = MutableLiveData(ScalingQuality.DEFAULT)
    val mouseMode = MutableLiveData(MouseMode.DEFAULT)
    val expertSettings = MutableLiveData(false)
    val debug = MutableLiveData(false)
    val cheatConfig = MutableLiveData(CheatConfig.DEFAULT)

    fun setVanillaGameDir(vanillaGameDirSet: String?) {
        vanillaGameDir.value = vanillaGameDirSet
    }

    fun setVanillaGameVersion(version: VanillaVersion) {
        vanillaGameVersion.value = version
    }

    fun setSaveGameDir(saveGameDirSet: String?) {
        saveGameDir.value = saveGameDirSet
    }

    fun setExternalizedJsonDir(externalizedJsonDirSet: String?) {
        externalizedJsonDir.value = externalizedJsonDirSet
    }

    fun setResolutionMode(mode: ResolutionMode) {
        resolutionMode.value = mode
    }

    fun setResolution(res: Resolution) {
        resolution.value = res
    }

    fun setScalingQuality(quality: ScalingQuality) {
        scalingQuality.value = quality
    }

    fun setMouseMode(mode: MouseMode) {
        mouseMode.value = mode
    }

    fun setExpertSettings(enabled: Boolean) {
        expertSettings.value = enabled
    }

    fun setDebug(enabled: Boolean) {
        debug.value = enabled
    }

    fun setCheatEnabled(enabled: Boolean) {
        cheatConfig.value = cheatConfig.value?.copy(enabled = enabled)
    }

    fun setCheatGodMode(enabled: Boolean) {
        cheatConfig.value = cheatConfig.value?.copy(godMode = enabled)
    }

    fun setCheatNonLethalPlayerDamage(enabled: Boolean) {
        cheatConfig.value = cheatConfig.value?.copy(nonLethalPlayerDamage = enabled)
    }

    fun setCheatFullMedicalHealing(enabled: Boolean) {
        cheatConfig.value = cheatConfig.value?.copy(fullMedicalHealing = enabled)
    }

    fun setCheatUnlimitedAmmo(enabled: Boolean) {
        cheatConfig.value = cheatConfig.value?.copy(unlimitedAmmo = enabled)
    }

    fun setCheatNoWeaponJam(enabled: Boolean) {
        cheatConfig.value = cheatConfig.value?.copy(noWeaponJam = enabled)
    }

    fun setCheatUnlimitedAP(enabled: Boolean) {
        cheatConfig.value = cheatConfig.value?.copy(unlimitedAP = enabled)
    }

    fun setCheatUnlimitedBreath(enabled: Boolean) {
        cheatConfig.value = cheatConfig.value?.copy(unlimitedBreath = enabled)
    }

    fun setCheatRevealEnemies(enabled: Boolean) {
        cheatConfig.value = cheatConfig.value?.copy(revealEnemies = enabled)
    }

    fun setCheatRevealItems(enabled: Boolean) {
        cheatConfig.value = cheatConfig.value?.copy(revealItems = enabled)
    }

    fun setCheatOneHitKill(enabled: Boolean) {
        cheatConfig.value = cheatConfig.value?.copy(oneHitKill = enabled)
    }

    fun setCheatPerfectHitChance(enabled: Boolean) {
        cheatConfig.value = cheatConfig.value?.copy(perfectHitChance = enabled)
    }
}
