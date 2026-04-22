package com.devkorea1m.weatherwake.sound

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.devkorea1m.weatherwake.databinding.FragmentSoundListBinding

class SoundListFragment : Fragment() {

    companion object {
        private const val ARG_CATEGORY = "category"
        fun newInstance(category: SoundCategory) = SoundListFragment().apply {
            arguments = Bundle().also { it.putString(ARG_CATEGORY, category.name) }
        }
    }

    private var _b: FragmentSoundListBinding? = null
    private val b get() = _b!!
    private val vm: SoundPickerViewModel by activityViewModels()

    override fun onCreateView(inf: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentSoundListBinding.inflate(inf, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val category = SoundCategory.valueOf(arguments?.getString(ARG_CATEGORY) ?: SoundCategory.NORMAL.name)
        val sounds = AlarmSoundManager.getByCategory(requireContext(), category)

        if (sounds.isEmpty()) {
            b.tvEmpty.visibility = View.VISIBLE
            b.recyclerView.visibility = View.GONE
            return
        }

        val adapter = SoundListAdapter(
            context     = requireContext(),
            sounds      = sounds,
            selectedUri = vm.selectedUri.value ?: "",
            onSelect    = { vm.select(it) }
        )

        b.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            this.adapter = adapter
        }

        // 선택 상태 변경 시 어댑터 갱신
        vm.selectedUri.observe(viewLifecycleOwner) {
            adapter.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
