package ru.acted.beatcharts

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import ru.acted.beatcharts.dataProcessors.AudioManager
import ru.acted.beatcharts.ui.theme.BeatChartsTheme
import ru.acted.beatcharts.ui.theme.googleSans
import java.io.File

class TestAudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TestPage()
        }
    }

    private fun playSound() {
        lifecycleScope.launch(Dispatchers.IO) {
            AudioManager().covertWemToMp3(File("/storage/emulated/0/beatstar/audiotest/audio.wem"), File("/storage/emulated/0/beatstar/audiotest/codebooks.bin"))?.let {
                val libvlc = LibVLC(this@TestAudioActivity)
                val mediaPlayer = MediaPlayer(libvlc).apply {
                    media = Media(libvlc, Uri.fromFile(it))
                    play()
                }
            }
        }
    }

    @Composable
    fun SuperButton(icon: Int?, buttonText: String, onClick: Runnable, color: Int) {
        Row(Modifier.clickable { onClick.run() }
            .clip(CutCornerShape(100.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .background(Color(color))
        ) {
            icon?.let { Image(bitmap = AppCompatResources.getDrawable(this@TestAudioActivity, R.drawable.angle_right)!!.toBitmap().asImageBitmap(), contentDescription = "") }
            Text(text = buttonText, fontSize = 16.sp, fontFamily = googleSans)
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun TestPage() {
        Surface() {
            BeatChartsTheme {
                Column() {
                    Button(onClick = {
                        playSound()
                    }, modifier = Modifier.apply { this.padding(0.dp) }) {
                        Text(text = stringResource(id = R.string.open_profile), fontSize = 16.sp, fontFamily = googleSans)
                    }
                    SuperButton(icon = null, buttonText = "Play audio file", onClick = {}, color = 0x11111)
                }
            }
        }

    }
}

