package ru.acted.beatcharts.pages

import android.os.AsyncTask
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import ru.acted.beatcharts.R
import ru.acted.beatcharts.databinding.FragmentSecretBinding
import java.net.HttpURLConnection
import java.net.URL

class Secret : Fragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AsyncTaskHandleJson().execute("http://worldtimeapi.org/api/timezone/etc/GMT")
    }

    private fun startSecretCountdown(currentTime: Long) {
        val mSecondsLeft: Long = 2045347200000 - currentTime*1000 ///
        object: CountDownTimer(mSecondsLeft, 1000) { //currentTime*1000
            //Здесь обновляем текст счетчика обратного отсчета с каждой секундой
            override fun onTick(millisUntilFinished: Long) {
                binding.specialTimer.text = "Доступно через: ${formatData(millisUntilFinished/1000)}"
            }

            //Задаем действия после завершения отсчета (высвечиваем надпись "Бабах!"):
            override fun onFinish() {
                binding.specialTimer.text = "Обновите приложение на сайте actedev.ru (если работает, конечно)"
            }
        }.start()
    }

    inner class AsyncTaskHandleJson: AsyncTask<String, String, String>() {
        override fun doInBackground(vararg url: String?): String {
            var text = ""

            val connection = URL(url[0]).openConnection() as HttpURLConnection
            try {
                connection.connect()
                text = connection.inputStream.use { it.reader().use{ reader -> reader.readText()}}
            } catch (e: java.lang.Exception) {
                text = "error"
            } finally {
                connection.disconnect()
            }

            return text
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if (result != null && result != "error") {
                val jMap = JSONObject(result!!).toMap()
                val current = (jMap["unixtime"] as Int).toLong()
                if (2045250000 - current > 0) startSecretCountdown(current) else binding.specialTimer.text = "Обновите приложение на сайте actedev.ru (если работает, конечно)"
            } else {
                binding.specialTimer.text = "Ошибка. Пожалуйста, попробуйте снова."
            }
        }

    }

    private fun JSONObject.toMap(): Map<String, *> = keys().asSequence().associateWith {
        when (val value = this[it])
        {
            is JSONArray ->
            {
                val map = (0 until value.length()).associate { Pair(it.toString(), value[it]) }
                JSONObject(map).toMap().values.toList()
            }
            is JSONObject -> value.toMap()
            JSONObject.NULL -> null
            else            -> value
        }
    }

    private fun formatData(secondsLeft: Long /*Seconds*/):String {
        var s = secondsLeft
        val y = resources.getString(R.string.years)
        val m = resources.getString(R.string.month)
        val d = resources.getString(R.string.days)
        val h = resources.getString(R.string.hours)
        val min = resources.getString(R.string.mins)
        val sec = resources.getString(R.string.seconds)

        val years = s / 31536000; s -= years * 31536000
        val months = (s / 2592000); s -= months * 2592000
        val days = (s / 86400); s -= days * 86400
        val hours = (s / 3600); s -= hours * 3600
        val minutes = (s / 60); s -= minutes * 60


        for (j in 0 until 6) {
            when (j) {
                0 -> {
                    //Check years
                    if (secondsLeft / 31536000 > 0)
                        return "$years $y, $months $m, $days $d"
                }
                1 -> {
                    //Check months
                    if (secondsLeft / 2592000 > 0) return "$months $m, $days $d, $hours $h"
                }
                2 -> {
                    //Check days
                    if (secondsLeft / 86400 > 0) return "$days $d, $hours $h, $minutes $min"
                }
                3 -> {
                    //Check hours
                    if (secondsLeft / 3600 > 0) return "$hours $h, $minutes $min, $s $sec"
                }
                4 -> {
                    //Check mins
                    if (secondsLeft / 60 > 0) return "$minutes $min, $s $sec"
                }
                5 -> {
                    //Return seconds
                    return "$secondsLeft $sec"
                }
            }
        }

        return ""
    }

    private var _binding: FragmentSecretBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentSecretBinding.inflate(inflater, container, false)
        return binding.root
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) = Secret()
    }
}