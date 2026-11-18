package vn.edu.dut.tiu.ui.home

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.databinding.FragmentSearchBinding
import vn.edu.dut.tiu.ui.common.appContainer

class SearchFragment : Fragment(R.layout.fragment_search) {

    private lateinit var adapter: HomeAdapter
    private val vm: SearchViewModel by viewModels {
        androidx.lifecycle.viewmodel.viewModelFactory {
            initializer { 
                SearchViewModel(appContainer.transactionRepository, appContainer.categoryRepository) 
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentSearchBinding.bind(view)

        b.btnBack.setOnClickListener { findNavController().popBackStack() }
        
        b.btnClear.setOnClickListener { 
            b.inputSearch.text.clear() 
        }

        adapter = HomeAdapter(
            onClick = { tx ->
                val args = Bundle().apply { putString("transactionId", tx.id) }
                findNavController().navigate(R.id.transactionEditorFragment, args)
            },
            onLongClick = { /* Ẩn nút xoá trong kết quả tìm kiếm */ }
        )
        b.recyclerResults.layoutManager = LinearLayoutManager(context)
        b.recyclerResults.adapter = adapter

        b.inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                b.btnClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                vm.search(query)
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            vm.searchResults.collectLatest { results ->
                adapter.submitList(results)
                
                if (b.inputSearch.text.isEmpty()) {
                    b.emptyState.visibility = View.VISIBLE
                    b.textEmpty.text = "Gõ từ khóa để tìm kiếm"
                    b.recyclerResults.visibility = View.GONE
                } else if (results.isEmpty()) {
                    b.emptyState.visibility = View.VISIBLE
                    b.textEmpty.text = "Không tìm thấy kết quả phù hợp"
                    b.recyclerResults.visibility = View.GONE
                } else {
                    b.emptyState.visibility = View.GONE
                    b.recyclerResults.visibility = View.VISIBLE
                }
            }
        }
    }
}
