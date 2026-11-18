package vn.edu.dut.tiu.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.databinding.FragmentOnboardingStep1Binding

class OnboardingStep1Fragment : Fragment(R.layout.fragment_onboarding_step1) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentOnboardingStep1Binding.bind(view)
        binding.btnStartWithTiu.setOnClickListener {
            findNavController().navigate(R.id.action_onboardingStep1_to_notificationOnboarding)
        }
        binding.linkLogin.setOnClickListener {
            findNavController().navigate(R.id.loginFragment)
        }
    }
}
