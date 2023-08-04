package com.example.bencanatracker.ui.reports

import AreaList
import ReportsAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bencanatracker.R
import com.example.bencanatracker.databinding.FragmentReportsBinding
import com.example.bencanatracker.response.Geometry
import com.example.bencanatracker.ui.map.SharedViewModel

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private lateinit var reportsAdapter: ReportsAdapter
    private lateinit var reportsViewModel: ReportsViewModel
    private lateinit var sharedViewModel: SharedViewModel
    private val areaList = AreaList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        reportsViewModel = ViewModelProvider(this).get(ReportsViewModel::class.java)
        sharedViewModel = ViewModelProvider(requireActivity()).get(SharedViewModel::class.java)
        setupRecyclerView()


        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        reportsViewModel.reportsLiveData.observe(viewLifecycleOwner) { reportsResponse ->
            // Store all data and update RecyclerView with initial data
            val reportsList = reportsResponse.result.objects.output.geometries
            allReportsData = reportsList
            filterRecyclerViewData(sharedViewModel.searchQuery.value, sharedViewModel.selectedProvince.value)
        }

        // Fetch reports data only if it hasn't been fetched already
        if (reportsViewModel.reportsLiveData.value == null) {
            reportsViewModel.fetchReportsData()
        }

        sharedViewModel.searchQuery.observe(viewLifecycleOwner) { searchQuery ->
            // Perform filtering logic based on searchQuery and selected province, then update the RecyclerView
            onSearchQueryChanged(searchQuery)
        }

        // Observe the selected province in SharedViewModel and update the RecyclerView accordingly
        sharedViewModel.selectedProvince.observe(viewLifecycleOwner) { selectedProvince ->
            onSearchQueryChanged(sharedViewModel.searchQuery.value)
        }
    }

    private fun setupRecyclerView() {
        reportsAdapter = ReportsAdapter(emptyList(), areaList) // Pass the areaList instance here
        binding.recyclerViewReports.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = reportsAdapter
        }
    }

    private var allReportsData: List<Geometry> = emptyList()
    private var filteredReportsData: List<Geometry> = emptyList()

    fun filterRecyclerViewData(searchQuery: String?, selectedProvince: String?) {
        filteredReportsData = if (searchQuery.isNullOrBlank() && selectedProvince.isNullOrBlank()) {
            allReportsData // No search query and no selected province, show all data
        } else {
            // Filter data based on searchQuery and/or selected province
            allReportsData.filter { geometry ->
                val instanceRegionCode = geometry.properties.tags.instanceRegionCode.orEmpty()
                val provinceName = areaList.ProvinceCode[instanceRegionCode].orEmpty()
                instanceRegionCode.contains(searchQuery.orEmpty(), ignoreCase = true) ||
                        provinceName.equals(selectedProvince.orEmpty(), ignoreCase = true)
            }
        }

        reportsAdapter.updateData(filteredReportsData)

        if (filteredReportsData.isEmpty()) {
            binding.textViewNoData.visibility = View.VISIBLE
            binding.recyclerViewReports.visibility = View.GONE
        } else {
            binding.textViewNoData.visibility = View.GONE
            binding.recyclerViewReports.visibility = View.VISIBLE
        }
    }

    private fun onSearchQueryChanged(searchQuery: String?) {
        filterRecyclerViewData(searchQuery, sharedViewModel.selectedProvince.value)
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun resetRecyclerView() {
        allReportsData = emptyList()
        filteredReportsData = emptyList()
        reportsViewModel.fetchReportsData()
    }
}