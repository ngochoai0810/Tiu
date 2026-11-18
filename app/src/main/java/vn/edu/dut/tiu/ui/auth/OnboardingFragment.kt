package vn.edu.dut.tiu.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.databinding.FragmentOnboardingBinding

class OnboardingFragment : Fragment(R.layout.fragment_onboarding) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentOnboardingBinding.bind(view)

        binding.btnGetStarted.setOnClickListener {
            findNavController().navigate(R.id.registerFragment)
        }

        binding.btnLoginDirect.setOnClickListener {
            findNavController().navigate(R.id.loginFragment)
        }
    }
}
