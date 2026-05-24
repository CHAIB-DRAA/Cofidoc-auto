package com.vsl.cofidocauto.model

import com.google.gson.annotations.SerializedName

data class Ride(
    @SerializedName("_id")       val id: String,
    @SerializedName("patientName")    val patientName: String,
    @SerializedName("patientPhone")   val patientPhone: String = "",
    @SerializedName("startLocation")  val startLocation: String,
    @SerializedName("endLocation")    val endLocation: String,
    @SerializedName("date")           val date: String,
    @SerializedName("startTime")      val startTime: String?,
    @SerializedName("endTime")        val endTime: String?,
    @SerializedName("type")           val type: String = "Aller",
    @SerializedName("realDistance")   val realDistance: Double?,
    @SerializedName("tolls")          val tolls: Double = 0.0,
    @SerializedName("status")         val status: String,
    @SerializedName("statuFacturation") val statuFacturation: String = "Non facturé",
    @SerializedName("motif")          val motif: String = "Consultation",
    @SerializedName("notes")          val notes: String = ""
)

data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val token: String, val user: UserInfo?)
data class UserInfo(val fullName: String, val email: String)
