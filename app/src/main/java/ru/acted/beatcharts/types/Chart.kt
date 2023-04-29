package ru.acted.beatcharts.types

data class Note (
    var type: Int = 0,
    var offsets: MutableList<Float> = mutableListOf(),
    var lane: Int = 0,
    var swipe: Int = 0,
    var size: Int = 0,
    var rawPos: MutableList<Long> = mutableListOf(),
)

//TODO implement note offset class for deluxe support
data class NoteOffset (
    var offset: Float = 0F,
    var lane: Int = 0,
)

data class Perfect (
    var offset: Float = 0.0F,
    var multiplier: Float = 0.0F
)

data class Speed (
    var offset: Float = 0.0F,
    var multiplier: Float = 0.0F
)

data class Effect (
    var offset: Float = 0.0F,
    var types: MutableList<Int> = mutableListOf()
)

data class Chart (
    var resolution: Int = 192, //Default value is 192
    var bpm: Int = 0,
    var notes: MutableList<Note> = mutableListOf(),
    var sections: MutableList<Float> = mutableListOf(),
    var perfects: MutableList<Perfect> = mutableListOf(),
    var speeds: MutableList<Speed> = mutableListOf(),
    var effects: MutableList<Effect> = mutableListOf()
)

data class ChartInfo (
    var title: String = "",
    var artist: String = "",
    var diff: Int = 0
)

data class ChartFiles (
    var info: ChartInfo = ChartInfo(),
    var chartBytes: MutableList<Byte> = mutableListOf(),
    var audioBytes: MutableList<Byte> = mutableListOf(),
    val colors: ChartColors = ChartColors(),
    var isAudioCompressed: Boolean = false,
    var artworkBytes: MutableList<Byte> = mutableListOf(),
    var configBytes: MutableList<Byte> = mutableListOf()
)

data class ChartColors (
    var circleGradient: Array<Int> = arrayOf(0, 0),
    var sideGradient: MutableList<Int> = mutableListOf(),
    var menuGradient: MutableList<Int> = mutableListOf(),

    var glowColor: Int = 0,
    var invertPerfectBar: Boolean = false,
    var vfxColor: Int = 0
)

data class ChartDefaults (
    //Normal defaults
    val normalPerfects: Array<Float> = arrayOf(1f, 0.9f, 0.8f, 0.7f, 0.6f),
    val normalSizes: Array<Float> = arrayOf(0.8f, 0.7f, 0.6f, 0.55f, 0.5f),
    //Hard defaults
    val hardPerfects: Array<Float> = arrayOf(0.9f, 0.8f, 0.7f, 0.6f, 0.6f),
    val hardSizes: Array<Float> = arrayOf(0.7f, 0.6f, 0.55f, 0.5f, 0.45f),
    //Extreme defaults
    val extremePerfects: Array<Float> = arrayOf(0.9f, 0.8f, 0.7f, 0.6f, 0.6f),
    val extremeSizes: Array<Float> = arrayOf(0.6f, 0.55f, 0.5f, 0.45f, 0.4f)
)