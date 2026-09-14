package com.pinakes.app.di

import com.pinakes.app.data.repository.PeriodicalsRepository
import com.pinakes.app.data.repository.StandaloneArticlesSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object StandaloneArticlesModule {
    @Provides
    fun source(repository: PeriodicalsRepository): StandaloneArticlesSource = repository
}
