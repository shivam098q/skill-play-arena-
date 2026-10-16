package com.example.services

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.launch

class AdMobService(private val context: Context) {
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private var isInitialized = false

    init {
        initialize()
    }

    private fun initialize() {
        if (!isInitialized) {
            isInitialized = true
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    // Set test device configuration to silence simulator telemetry warnings
                    val requestConfiguration = com.google.android.gms.ads.RequestConfiguration.Builder()
                        .setTestDeviceIds(listOf(com.google.android.gms.ads.AdRequest.DEVICE_ID_EMULATOR))
                        .build()
                    MobileAds.setRequestConfiguration(requestConfiguration)

                    MobileAds.initialize(context) {
                        loadRewardedAd()
                    }
                } catch (e: Exception) {
                    Log.e("AdMobService", "Failed to init MobileAds", e)
                }
            }
        }
    }

    fun loadRewardedAd(onLoaded: () -> Unit = {}, onFailed: () -> Unit = {}) {
        if (rewardedAd != null || isLoading) {
            if (rewardedAd != null) onLoaded()
            return
        }

        isLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            AdMobConfig.REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false
                    onLoaded()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w("AdMobService", "RewardedAd failed to load: ${loadAdError.message}")
                    rewardedAd = null
                    isLoading = false
                    onFailed()
                }
            }
        )
    }

    fun isAdAvailable(): Boolean = rewardedAd != null

    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: (amount: Int) -> Unit,
        onAdDismissed: () -> Unit,
        onAdFailed: () -> Unit
    ) {
        val currentAd = rewardedAd
        if (currentAd == null) {
            // Ad not ready, notify failure so UI can offer fallback simulation
            onAdFailed()
            loadRewardedAd()
            return
        }

        var rewardGranted = false

        currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewardedAd()
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e("AdMobService", "Ad failed to show: ${adError.message}")
                rewardedAd = null
                loadRewardedAd()
                onAdFailed()
            }

            override fun onAdShowedFullScreenContent() {
                rewardedAd = null
            }
        }

        currentAd.show(activity) { rewardItem ->
            rewardGranted = true
            onRewardEarned(rewardItem.amount)
        }
    }
}
