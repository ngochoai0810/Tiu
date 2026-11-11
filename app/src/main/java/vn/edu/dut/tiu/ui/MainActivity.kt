package vn.edu.dut.tiu.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.TiuApp
import vn.edu.dut.tiu.databinding.ActivityMainBinding

/** Single-Activity: mọi màn hình là Fragment trong nav_graph. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val bottomNavDestinations = setOf(
        R.id.homeFragment, R.id.statsFragment, R.id.cameraFragment, R.id.budgetFragment, R.id.accountFragment,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // targetSdk 35 bật edge-to-edge: chừa chỗ cho status bar / navigation bar / bàn phím.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navController = navHost.navController

        // Đã đăng nhập -> vào thẳng Nhật ký, chưa -> màn Đăng nhập.
        val signedIn = (application as TiuApp).container.authRepository.currentUserId != null
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(if (signedIn) R.id.homeFragment else R.id.loginFragment)
        navController.setGraph(graph, null)

        binding.bottomNav.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility =
                if (destination.id in bottomNavDestinations) View.VISIBLE else View.GONE
        }
    }
}
