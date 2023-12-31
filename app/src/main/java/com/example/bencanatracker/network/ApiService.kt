package com.example.bencanatracker.network

import com.example.bencanatracker.response.FloodResponse
import com.example.bencanatracker.response.ReportsResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {

    @GET("reports")
    suspend fun getReports(
        @Query("admin") admin: String? = null,
        @Query("disaster") disasterType: String? = null,
        @Query("timeperiod") timePeriod: Long = 604800
    ): ReportsResponse



    @GET("floods")
    suspend fun getFloodData(
        @Query("admin") admin: String,
        @Query("minimum_state") minimumState: Int
    ): FloodResponse
}
