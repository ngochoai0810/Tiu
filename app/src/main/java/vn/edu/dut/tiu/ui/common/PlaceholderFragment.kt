package vn.edu.dut.tiu.ui.common

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.databinding.FragmentPlaceholderBinding

/** Màn tạm cho các tính năng của tuần sau (Thống kê, Chụp ảnh). */
class PlaceholderFragment : Fragment(R.layout.fragment_placeholder) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPlaceholderBinding.bind(view)
        val args = requireArguments()
        binding.textTitle.setText(args.getInt("title"))
        binding.textBody.setText(args.getInt("body"))
    }
}
