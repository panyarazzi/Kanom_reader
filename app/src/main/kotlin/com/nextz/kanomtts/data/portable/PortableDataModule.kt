package com.nextz.kanomtts.data.portable

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PortableDataModule {
    @Binds
    @Singleton
    abstract fun bindPortableDataGateway(implementation: PortableDataManager): PortableDataGateway
}
