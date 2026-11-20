package com.nextz.kanomtts.playback.di

import javax.inject.Qualifier

/** Qualifier for the application-scoped playback [kotlinx.coroutines.CoroutineScope]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlaybackScope
