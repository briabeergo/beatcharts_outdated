package ru.acted.beatcharts.utils

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream


class FileZipUtils {
    companion object {
        private val buffer = 12582912

        fun zip(_files: Array<String>, zipFileName: String?) {
            try {
                var origin: BufferedInputStream? = null
                val dest = FileOutputStream(zipFileName)
                val out = ZipOutputStream(
                    BufferedOutputStream(
                        dest
                    )
                )
                val data = ByteArray(buffer)
                for (i in _files.indices) {
                    val fi = FileInputStream(_files[i])
                    origin = BufferedInputStream(fi, buffer)
                    val entry = ZipEntry(_files[i].substring(_files[i].lastIndexOf("/") + 1))
                    out.putNextEntry(entry)
                    var count: Int
                    while (origin.read(data, 0, buffer).also { count = it } != -1) {
                        out.write(data, 0, count)
                    }
                    origin.close()
                }
                out.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun unzip(_zipFile: String?, _targetLocation: String) {

            //create target location folder if not exist
            dirChecker(_targetLocation)

            try {
                val fin = FileInputStream(_zipFile)
                val zin = ZipInputStream(fin)
                var ze: ZipEntry? = null
                while (zin.nextEntry.also { ze = it } != null) {

                    //create dir if required while unzipping
                    if (ze!!.isDirectory) {
                        dirChecker(ze!!.name)
                    } else {
                        val fout = FileOutputStream(_targetLocation + ze!!.name)
                        var c = zin.read()
                        while (c != -1) {
                            fout.write(c)
                            c = zin.read()
                        }
                        zin.closeEntry()
                        fout.close()
                    }
                }
                zin.close()
            } catch (e: java.lang.Exception) {
                println(e)
            }
        }

        private fun dirChecker(_targetLocation: String) {
            File(_targetLocation).mkdirs()
        }
    }
}