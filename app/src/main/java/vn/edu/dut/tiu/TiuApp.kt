package vn.edu.dut.tiu

import android.app.Application
import vn.edu.dut.tiu.di.AppContainer

class TiuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
