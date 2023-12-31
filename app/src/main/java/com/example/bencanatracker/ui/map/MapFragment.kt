package com.example.bencanatracker.ui.map

import AreaList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.bencanatracker.R
import com.example.bencanatracker.databinding.FragmentMapBinding
import com.example.bencanatracker.response.Geometry
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetDialog


class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private var mGoogleMap: GoogleMap? = null
    private val areaList = AreaList()
    private val DEFAULT_LATITUDE = -2.5489
    private val DEFAULT_LONGITUDE = 118.0149
    private var filterDialog: BottomSheetDialog? = null

    val binding get() = _binding!!
    private lateinit var loadingLayout: View


    private val sharedViewModel: SharedViewModel by lazy {
        ViewModelProvider(requireActivity()).get(SharedViewModel::class.java)
    }

    private val mapViewModel: MapViewModel by lazy {
        ViewModelProvider(this).get(MapViewModel::class.java)
    }

    private var searchedLocationLatLng: LatLng? = null

    interface OnResetClickListener {
        fun onResetButtonClick()
    }

    private var resetClickListener: OnResetClickListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        val root: View = binding.root
        val mapFragment =
            childFragmentManager.findFragmentById(R.id.mapfragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        loadingLayout = root.findViewById(R.id.loadingLayout1)


        // Initialize the filter dialog if not already initialized
        if (filterDialog == null) {
            initFilterDialog()
        }

        // Set click listener on the filter button
        binding.filterButton.setOnClickListener { view ->
            showFilterDialog()
        }

        // Set click listener on the Reset button
        binding.resetButton.setOnClickListener {
            resetMap()
            resetClickListener?.onResetButtonClick() // Notify MainActivity that Reset button is clicked
        }

        // Setup autocomplete for search bar
        setupAutocompleteSearch()

        // Observe the reportsList LiveData and call addMarkersFromApi when it changes
        mapViewModel.reportsList.observe(viewLifecycleOwner) { reportsList ->
            addMarkersFromApi(reportsList)
        }

        return root
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mGoogleMap = googleMap

        loadingLayout.visibility = View.VISIBLE

        // Check if the map is not null before calling fetchReportsAndAddMarkers
        mGoogleMap?.let {
            // Fetch reports data and add markers to the map
            mapViewModel.fetchReportsAndAddMarkers()

            // Enable zoom controls (+/- buttons) on the map
            mGoogleMap?.uiSettings?.isZoomControlsEnabled = true
        }
    }

    private fun initFilterDialog() {
        filterDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_filters, null)
        filterDialog?.setContentView(view)

        val spinnerDisasterType: Spinner = view.findViewById(R.id.spinnerDisasterType)
        val btnApplyFilter = view.findViewById<Button>(R.id.buttonApplyFilter)
        val btnCancel = view.findViewById<Button>(R.id.buttonCancel) // Get reference to the Cancel button

        spinnerDisasterType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                btnApplyFilter.setOnClickListener {
                    applyFilter()
                    filterDialog?.dismiss()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Handle the case when nothing is selected (optional)
            }
        }

        // Set the OnClickListener for the Cancel button
        btnCancel.setOnClickListener {
            filterDialog?.dismiss()
        }
    }

    private fun applyFilter() {
        val disasterType = filterDialog?.findViewById<Spinner>(R.id.spinnerDisasterType)?.selectedItem.toString()
        if (disasterType.isNotBlank()) {
            val selectedProvince = sharedViewModel.selectedProvince.value
            mapViewModel.fetchReportsAndAddMarkers(
                selectedDisasterType = disasterType,
                selectedProvince = selectedProvince,
                applyFilter = true
            )
        }
    }



    private fun setupAutocompleteSearch() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            areaList.ProvinceCode.values.toMutableList()
        )
        binding.searchBar.threshold = 1
        binding.searchBar.setAdapter(adapter)
        binding.searchBar.setOnItemClickListener { _, _, position, _ ->
            val selectedProvinceName = adapter.getItem(position)
            val nonNullProvinceName = selectedProvinceName ?: return@setOnItemClickListener
            val latLng = areaList.getLatLngByProvinceName(nonNullProvinceName)
            if (latLng != null) {
                searchedLocationLatLng = latLng
                sharedViewModel.setSearchQuery(nonNullProvinceName)
                sharedViewModel.setSelectedProvince(nonNullProvinceName)

                zoomToLocation(latLng)
            } else {
                // Handle the case when the province name doesn't have a corresponding latitude and longitude
                // You may want to display an error message or handle this situation differently
            }
        }
    }

    private fun zoomToLocation(location: LatLng) {
        val zoomLevel = 7.0f // Adjust the zoom level as needed

        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(location, zoomLevel)
        mGoogleMap?.moveCamera(cameraUpdate)
    }

    private fun addMarkersFromApi(reportsList: List<Geometry>) {
        // Clear the previous markers
        mGoogleMap?.clear()

        val markerList = mutableListOf<Marker>()

        val selectedProvince = sharedViewModel.selectedProvince.value

        for (geometry in reportsList) {
            val lat = geometry.coordinates[1] // Latitude is at index 1
            val lng = geometry.coordinates[0] // Longitude is at index 0
            val instanceRegionCode = geometry.properties.tags.instanceRegionCode ?: "Unknown Region"
            val provinceName = areaList.ProvinceCode[instanceRegionCode] ?: "Unknown Province"
            val reportType = geometry.properties.disasterType ?: "Unknown Report Type"

            val title = "$provinceName - $reportType" // Concatenate province name and report type

            if (selectedProvince.isNullOrBlank() || provinceName.equals(selectedProvince, ignoreCase = true)) {
                val latLng = LatLng(lat, lng)
                val markerOptions = MarkerOptions().position(latLng).title(title)
                val marker = mGoogleMap?.addMarker(markerOptions)
                marker?.let { markerList.add(it) }
            }
        }

        loadingLayout.visibility = View.GONE

        // If there are markers, include the searched location marker (if any) in the bounds
        if (markerList.isNotEmpty() || searchedLocationLatLng != null) {
            val boundsBuilder = LatLngBounds.Builder()
            for (marker in markerList) {
                boundsBuilder.include(marker.position)
            }
            searchedLocationLatLng?.let { boundsBuilder.include(it) }

            val bounds = boundsBuilder.build()
            val padding = 50 // Adjust padding as needed

            // Set the camera position and zoom level based on the markers' bounds
            val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
            mGoogleMap?.moveCamera(cameraUpdate)
        } else {
            // If there are no markers and no searched location, show a default location (e.g., a city center) with zoom level 7.0.
        }
    }



    private fun showFilterDialog() {
        filterDialog?.show()
    }

    private fun resetMap() {
        searchedLocationLatLng = null

        // Fetch all reports and add markers to the map
        mapViewModel.fetchReportsAndAddMarkers()

        // Clear the search query and selected province in the SharedViewModel
        sharedViewModel.setSearchQuery("")
        sharedViewModel.setSelectedProvince("")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}