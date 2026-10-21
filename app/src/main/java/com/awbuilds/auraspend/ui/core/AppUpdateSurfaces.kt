package com.awbuilds.auraspend.ui.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.awbuilds.auraspend.AuraSpendApp
import com.awbuilds.auraspend.BuildConfig
import com.awbuilds.auraspend.data.update.AppUpdateController
import com.awbuilds.auraspend.data.update.AppUpdatePrompt
import com.awbuilds.auraspend.data.update.UpdatePolicy
import com.awbuilds.auraspend.ui.designsystem.SettingsAvatarButton

/**
 * The Play updater for this build, or null where there is none.
 *
 * `IN_APP_UPDATES` is false for dev and fdroid, so those builds never
 * instantiate it and never render an update surface they cannot honour.
 */
@Composable
fun rememberAppUpdateController(): AppUpdateController? {
    val context = LocalContext.current
    return remember(context) {
        if (BuildConfig.IN_APP_UPDATES) {
            (context.applicationContext as? AuraSpendApp)?.appUpdateController
        } else {
            null
        }
    }
}

/**
 * The one prompt the in-app surfaces should show.
 *
 * Every caller asks this same question, so the order between "a download
 * finished", "this release is urgent" and "the user dismissed it" is decided
 * once, in [UpdatePolicy], instead of differently on each screen.
 */
@Composable
fun rememberUpdatePrompt(): AppUpdatePrompt {
    val controller = rememberAppUpdateController() ?: return AppUpdatePrompt.None
    val availability by controller.availability.collectAsStateWithLifecycle()
    val dismissed by controller.dismissedInstalledVersion.collectAsStateWithLifecycle()
    val known = availability ?: return AppUpdatePrompt.None
    return UpdatePolicy.inApp(known, dismissed)
}

/**
 * The header avatar, with a dot when Settings has something to say.
 *
 * The dot is scoped to the banner prompt and nothing else: a finished download
 * gets its own card (it needs a tap, not a hint), and a dismissal the user
 * already made must not keep a mark on the header.
 */
@Composable
fun UpdateAwareSettingsAvatar(onClick: () -> Unit) {
    SettingsAvatarButton(
        onClick = onClick,
        showUpdateBadge = rememberUpdatePrompt() == AppUpdatePrompt.Banner
    )
}
