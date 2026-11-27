package org.uranxsu.manager.ui.component.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.FolderDelete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.uranxsu.manager.R
import org.uranxsu.manager.domain.model.AppProfile
import org.uranxsu.manager.ui.component.settings.SettingsSwitchWidget
import org.uranxsu.manager.ui.util.CustomTextKey
import org.uranxsu.manager.ui.util.customText

@Composable
fun AppProfileConfig(
    enabled: Boolean,
    profile: AppProfile,
    defaultUmountModules: Boolean = profile.umountModules,
    onProfileChange: (AppProfile) -> Unit,
) {
    SettingsSwitchWidget(
        icon = Icons.TwoTone.FolderDelete,
        title = customText(CustomTextKey.SUPERUSER_UMOUNT_MODULES),
        description = stringResource(R.string.profile_umount_modules_summary),
        checked = if (enabled) {
            profile.umountModules
        } else {
            defaultUmountModules
        },
        enabled = enabled,
        onCheckedChange = {
            onProfileChange(
                profile.copy(
                    umountModules = it,
                    nonRootUseDefault = false,
                ),
            )
        },
    )
}

@Preview
@Composable
private fun AppProfileConfigPreview() {
    var profile by remember { mutableStateOf(AppProfile("")) }
    AppProfileConfig(enabled = false, profile = profile) {
        profile = it
    }
}
