package com.example.data

import com.google.gson.annotations.SerializedName

data class UserModel(
    @SerializedName("id") var id: String,
    @SerializedName("usuario") val usuario: String,
    @SerializedName("password") val password: String,
    @SerializedName("vencimiento") val vencimiento: String,
    @SerializedName("adultos") val adultos: Boolean
)
