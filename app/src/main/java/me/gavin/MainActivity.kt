package me.gavin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Consumer
import kotlinx.android.synthetic.main.activity_main_2.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_2)

        cal.dateSelectedListener = Consumer {
            supportActionBar?.title = "%tc".format(it)
        }
    }
}
