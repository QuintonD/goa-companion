package dev.quinton.goa.feature.timer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import dev.quinton.goa.feature.timer.data.AndroidTimerAssets
import dev.quinton.goa.feature.timer.data.TimerAssets
import dev.quinton.goa.feature.timer.system.AlarmTimerScheduler
import dev.quinton.goa.feature.timer.system.AndroidCuePlayer
import dev.quinton.goa.feature.timer.system.AndroidTimerNotifier
import dev.quinton.goa.feature.timer.system.BootCountProvider
import dev.quinton.goa.feature.timer.system.CuePlayer
import dev.quinton.goa.feature.timer.system.SettingsBootCountProvider
import dev.quinton.goa.feature.timer.system.SystemElapsedClock
import dev.quinton.goa.feature.timer.system.SystemWallClock
import dev.quinton.goa.feature.timer.system.TimerNotifier
import dev.quinton.goa.feature.timer.system.TimerScheduler
import dev.quinton.goa.feature.timer.ui.setup.CoinFlipper
import dev.quinton.goa.feature.timer.ui.setup.RandomCoinFlipper
import dev.quinton.goa.feature.timer.ui.timer.FrameTicker
import dev.quinton.goa.feature.timer.ui.timer.Ticker
import javax.inject.Qualifier

/** Clocks used by the timer; qualified so other features may bind their own without clashes. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TimerClock

@Module
@InstallIn(SingletonComponent::class)
internal interface TimerModule {
    @Binds fun assets(impl: AndroidTimerAssets): TimerAssets

    @Binds fun scheduler(impl: AlarmTimerScheduler): TimerScheduler

    @Binds fun notifier(impl: AndroidTimerNotifier): TimerNotifier

    @Binds fun cues(impl: AndroidCuePlayer): CuePlayer

    @Binds fun coin(impl: RandomCoinFlipper): CoinFlipper

    @Binds fun ticker(impl: FrameTicker): Ticker

    @Binds fun boot(impl: SettingsBootCountProvider): BootCountProvider

    @Binds @TimerClock
    fun elapsed(impl: SystemElapsedClock): ElapsedClock

    @Binds @TimerClock
    fun wall(impl: SystemWallClock): WallClock
}
