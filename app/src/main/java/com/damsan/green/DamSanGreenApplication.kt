package com.damsan.green

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.damsan.green.utils.HandlesOwnInsets
import com.damsan.green.utils.SafeArea

class DamSanGreenApplication : Application(), Application.ActivityLifecycleCallbacks {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) {
        if (activity !is HandlesOwnInsets) {
            SafeArea.apply(activity, activity.findViewById(android.R.id.content))
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
