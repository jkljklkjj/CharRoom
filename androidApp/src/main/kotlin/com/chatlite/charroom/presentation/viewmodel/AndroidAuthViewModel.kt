package com.chatlite.charroom.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import presentation.viewmodel.AuthViewModel

/**
 * Android平台的AuthViewModel包装类
 * 继承自AndroidX ViewModel，使用viewModelScope作为协程作用域
 */
class AndroidAuthViewModel : ViewModel() {
    val viewModel = AuthViewModel(
        coroutineScope = viewModelScope
    )
}