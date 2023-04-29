package ru.acted.beatcharts.types

import android.graphics.Bitmap

class Song {

    var id = ""
    var charter = ""
    var title = ""
    var artist = ""
    var directoryPath = ""
    var artworkPath = ""
    var baseColor = ""
    var isColorDark = false
    var problem = mutableListOf<Int>()
    var diff = "1"
    var isHidden = false
    var artBitmap: Bitmap? = null
    var cardYCoordinate: Int = 0

}