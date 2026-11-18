package vn.edu.dut.tiu

import android.app.Application
import vn.edu.dut.tiu.di.AppContainer

class TiuApp : Application() {
    lateinit var container: AppContainer
        private set

    companion object {
        lateinit var instance: TiuApp
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer()
    }
}
