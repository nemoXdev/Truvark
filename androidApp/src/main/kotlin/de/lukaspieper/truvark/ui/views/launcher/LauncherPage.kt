/*
 * SPDX-FileCopyrightText: 2022 Lukas Pieper
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.lukaspieper.truvark.ui.views.launcher

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.biometric.AuthenticationRequest
import androidx.biometric.AuthenticationRequest.Biometric.Strength
import androidx.biometric.AuthenticationResult
import androidx.biometric.BiometricPrompt
import androidx.biometric.compose.rememberAuthenticationLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import de.lukaspieper.truvark.ListPaneRoute
import de.lukaspieper.truvark.R
import de.lukaspieper.truvark.Route
import de.lukaspieper.truvark.SinglePaneRoute
import de.lukaspieper.truvark.ui.controls.PasswordField
import de.lukaspieper.truvark.ui.controls.SafeDrawingScaffold
import de.lukaspieper.truvark.ui.controls.ShapedIcon
import de.lukaspieper.truvark.ui.controls.ShapedImage
import de.lukaspieper.truvark.ui.controls.SingleLineText
import de.lukaspieper.truvark.ui.preview.PagePreviews
import de.lukaspieper.truvark.ui.preview.PreviewHost
import de.lukaspieper.truvark.ui.theme.paddings
import de.lukaspieper.truvark.ui.views.launcher.LauncherViewModel.LauncherState.DIRECTORY_SELECTION
import de.lukaspieper.truvark.ui.views.launcher.LauncherViewModel.LauncherState.DONE
import de.lukaspieper.truvark.ui.views.launcher.LauncherViewModel.LauncherState.NONE
import de.lukaspieper.truvark.ui.views.launcher.LauncherViewModel.LauncherState.PROCESSING
import de.lukaspieper.truvark.ui.views.launcher.LauncherViewModel.LauncherState.VAULT_CREATION
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

@OptIn(ExperimentalPermissionsApi::class)
@Composable
public fun LauncherPage(
    navigateAndClearBackStack: (Route) -> Unit,
    navigateTo: (Route) -> Unit,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val activity = LocalActivity.current!!

    LaunchedEffect(viewModel.state, navigateAndClearBackStack) {
        if (viewModel.state == DONE) {
            navigateAndClearBackStack(SinglePaneRoute.Browser(viewModel.vaultConfig!!.id))
        }
    }

    val notificationPermissionState = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        }

        else -> null
    }

    val authLauncher = rememberAuthenticationLauncher(resultCallback = { result ->
        when (result) {
            is AuthenticationResult.Success -> result.crypto?.cipher?.let { viewModel.unlockWithCipher(it) }
            is AuthenticationResult.Error -> {
                logcat("LauncherPage", LogPriority.WARN) {
                    "Biometric unlocking failed: ${result.errorCode} '${result.errString}'"
                }

                val userCausedErrors = listOf(
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED
                )
                if (result.errorCode !in userCausedErrors) {
                    viewModel.disableBiometricUnlockingBecauseOfError()
                }
            }

            else -> {}
        }
    })

    val isAnyDebuggingSettingEnabled = viewModel.isAnyDebuggingSettingEnabled.collectAsStateWithLifecycle(false)
    LauncherView(
        notificationPermissionState = notificationPermissionState,
        state = viewModel.state,
        updateState = { viewModel.state = it },
        vaultDisplayName = viewModel.vaultConfig?.name ?: "",
        biometricUnlockingSupported = viewModel.supportsBiometricUnlocking,
        unlockingErrorText = viewModel.unlockingErrorText,
        unlockVaultWithPassword = viewModel::unlockVaultWithPassword,
        navigateToSettings = { navigateTo(ListPaneRoute.SettingsHome(vaultId = null)) },
        showBiometricPrompt = {
            try {
                authLauncher.launch(
                    AuthenticationRequest.Biometric.Builder(title = activity.getString(R.string.biometric_unlocking))
                        .setMinStrength(Strength.Class3(viewModel.getCryptoObject()))
                        .setIsConfirmationRequired(true)
                        .build()
                )
            } catch (e: Exception) {
                logcat("LauncherPage", LogPriority.ERROR) { e.asLog() }
                viewModel.disableBiometricUnlockingBecauseOfError()
            }
        },
        setupDialog = {
            SetupDialog(
                state = viewModel.state,
                updateState = { viewModel.state = it },
                inspectDirectory = viewModel::inspectDirectory,
                createVault = viewModel::createVault
            )
        },
        isAnyDebuggingSettingEnabled = isAnyDebuggingSettingEnabled.value,
        modifier = modifier
    )
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LauncherView(
    notificationPermissionState: PermissionState?,
    state: LauncherViewModel.LauncherState,
    vaultDisplayName: String,
    biometricUnlockingSupported: Boolean,
    unlockingErrorText: Int?,
    updateState: (LauncherViewModel.LauncherState) -> Unit,
    unlockVaultWithPassword: (ByteArray) -> Unit,
    navigateToSettings: () -> Unit,
    showBiometricPrompt: () -> Unit,
    setupDialog: @Composable () -> Unit,
    isAnyDebuggingSettingEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    SafeDrawingScaffold(
        largeTopAppBarTitle = stringResource(R.string.app_name),
        largeTopAppBarActions = {
            IconButton(
                onClick = navigateToSettings,
                content = { Icon(Icons.Default.Settings, null) }
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        AdaptivePane(
            firstPane = {
                LauncherInfoCardPager(
                    isAnyDebuggingSettingEnabled = isAnyDebuggingSettingEnabled,
                    isVaultAvailable = vaultDisplayName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            secondPane = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                    verticalArrangement = spacedBy(MaterialTheme.paddings.extraLarge),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.sizeIn(maxWidth = 550.dp)
                    ) {
                        if (notificationPermissionState?.status is PermissionStatus.Denied) {
                            NotificationPermissionView(notificationPermissionState)
                        } else {
                            if (vaultDisplayName.isNotBlank()) {
                                VaultUnlockCardView(
                                    vaultDisplayName = vaultDisplayName,
                                    biometricUnlockingSupported = biometricUnlockingSupported,
                                    unlockingErrorText = unlockingErrorText,
                                    unlockVaultWithPassword = unlockVaultWithPassword,
                                    showBiometricPrompt = showBiometricPrompt
                                )
                            } else {
                                NoVaultCardView()
                            }
                        }
                    }

                    if (notificationPermissionState?.status !is PermissionStatus.Denied) {
                        val size = ButtonDefaults.MediumContainerHeight
                        FilledTonalButton(
                            onClick = { updateState(DIRECTORY_SELECTION) },
                            modifier = Modifier
                                .heightIn(size)
                                .width(550.dp),
                            contentPadding = ButtonDefaults.contentPaddingFor(size, hasStartIcon = true),
                        ) {
                            Icon(
                                Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.iconSizeFor(size)),
                            )
                            Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(size)))
                            Text(
                                stringResource(R.string.create_or_open_vault),
                                style = ButtonDefaults.textStyleFor(size)
                            )
                        }

                        if (state in listOf(DIRECTORY_SELECTION, VAULT_CREATION, PROCESSING)) {
                            setupDialog()
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
        )
    }
}

@Composable
private fun AdaptivePane(
    firstPane: @Composable () -> Unit,
    secondPane: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val firstPaneContent = remember { movableContentOf { firstPane() } }
    val secondPaneContent = remember { movableContentOf { secondPane() } }

    if (isLandscape) {
        Row(
            horizontalArrangement = spacedBy(MaterialTheme.paddings.extraLarge),
            modifier = modifier
        ) {
            Box(Modifier.weight(0.4f)) { firstPaneContent() }
            Box(
                Modifier
                    .weight(0.6f)
                    .align(Alignment.CenterVertically)
            ) { secondPaneContent() }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
        ) {
            firstPaneContent()
            secondPaneContent()
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun NotificationPermissionView(notificationPermissionState: PermissionState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var requestPermissionCounter by rememberSaveable { mutableIntStateOf(0) }
    var permissionRequestCompleted by rememberSaveable { mutableStateOf(false) }

    with(notificationPermissionState) {
        LaunchedEffect(status) {
            if (requestPermissionCounter > 0) {
                permissionRequestCompleted = true
            }
        }

        val navigateToSettings by remember {
            derivedStateOf {
                requestPermissionCounter > 1 || (permissionRequestCompleted && !status.shouldShowRationale)
            }
        }

        Column(
            modifier = modifier.padding(all = MaterialTheme.paddings.large),
            verticalArrangement = spacedBy(MaterialTheme.paddings.large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ShapedIcon(
                imageVector = Icons.Default.Notifications,
                tint = MaterialTheme.colorScheme.primary,
                shape = MaterialShapes.Sunny
            )

            Column(
                verticalArrangement = spacedBy(MaterialTheme.paddings.small),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.notification_permission_title),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.notification_permission_description),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    if (navigateToSettings) {
                        context.startActivity(
                            Intent().apply {
                                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        )
                    } else {
                        launchPermissionRequest()
                        requestPermissionCounter++
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (navigateToSettings) Icons.Default.Settings else Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    text = if (navigateToSettings) {
                        stringResource(R.string.open_app_settings)
                    } else {
                        stringResource(R.string.grant_permission)
                    }
                )
            }
        }
    }
}

@Composable
private fun NoVaultCardView(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = spacedBy(MaterialTheme.paddings.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(MaterialTheme.paddings.large)
    ) {
        ShapedImage(
            painter = painterResource(R.drawable.ic_locker),
            background = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialShapes.Cookie12Sided
        )

        Column(
            verticalArrangement = spacedBy(MaterialTheme.paddings.small),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.no_vault_found_title),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.no_existing_vault_info),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VaultUnlockCardView(
    vaultDisplayName: String,
    biometricUnlockingSupported: Boolean,
    unlockingErrorText: Int?,
    unlockVaultWithPassword: (ByteArray) -> Unit,
    showBiometricPrompt: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(MaterialTheme.paddings.extraLarge)
    ) {
        ShapedImage(
            painter = painterResource(R.drawable.ic_locker),
            background = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialShapes.Cookie12Sided
        )
        Spacer(Modifier.width(MaterialTheme.paddings.large))
        SingleLineText(
            text = vaultDisplayName,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineLarge
        )
    }

    Column(
        verticalArrangement = spacedBy(MaterialTheme.paddings.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(MaterialTheme.paddings.large)
    ) {
        Column(
            verticalArrangement = spacedBy(MaterialTheme.paddings.medium),
            modifier = Modifier.fillMaxWidth()
        ) {
            PasswordUnlockView(unlockVaultWithPassword, unlockingErrorText)

            if (biometricUnlockingSupported) {
                Button(
                    onClick = showBiometricPrompt,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.biometric_unlocking))
                }
            }
        }
    }
}

@Composable
private fun PasswordUnlockView(
    unlockWithPassword: (ByteArray) -> Unit,
    @StringRes errorMessageResource: Int?
) {
    if (errorMessageResource != null && errorMessageResource != R.string.incorrect_password) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(errorMessageResource),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(MaterialTheme.paddings.medium)
            )
        }
    }

    Row(
        horizontalArrangement = spacedBy(MaterialTheme.paddings.medium),
        modifier = Modifier.fillMaxWidth()
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val passwordState = remember { TextFieldState() }
        PasswordField(
            state = passwordState,
            onKeyboardDone = {
                keyboardController?.hide()
                unlockWithPassword(passwordState.text.toString().toByteArray())
            },
            passwordIsIncorrect = errorMessageResource == R.string.incorrect_password,
            modifier = Modifier.weight(1f)
        )

        Button(
            onClick = {
                keyboardController?.hide()
                unlockWithPassword(passwordState.text.toString().toByteArray())
            },
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .height(TextFieldDefaults.MinHeight)
        ) {
            Icon(Icons.Default.LockOpen, null)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@PagePreviews
@Composable
private fun NoNotificationPermissionPreview() = PreviewHost {
    LauncherView(
        notificationPermissionState = object : PermissionState {
            override val permission: String = ""
            override val status: PermissionStatus = PermissionStatus.Denied(false)

            override fun launchPermissionRequest() {
                // Previews do not need implementations.
            }
        },
        state = NONE,
        vaultDisplayName = "",
        biometricUnlockingSupported = false,
        unlockingErrorText = null,
        updateState = {},
        unlockVaultWithPassword = {},
        navigateToSettings = {},
        showBiometricPrompt = {},
        setupDialog = {},
        isAnyDebuggingSettingEnabled = true
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@PagePreviews
@Composable
private fun NoVaultSelectedPreview() = PreviewHost {
    LauncherView(
        notificationPermissionState = null,
        state = NONE,
        vaultDisplayName = "",
        biometricUnlockingSupported = false,
        unlockingErrorText = null,
        updateState = {},
        unlockVaultWithPassword = {},
        navigateToSettings = {},
        showBiometricPrompt = {},
        setupDialog = {},
        isAnyDebuggingSettingEnabled = false
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@PagePreviews
@Composable
private fun VaultSelectedPreview() = PreviewHost {
    LauncherView(
        notificationPermissionState = null,
        state = NONE,
        vaultDisplayName = "Vault",
        biometricUnlockingSupported = true,
        unlockingErrorText = null,
        updateState = {},
        unlockVaultWithPassword = {},
        navigateToSettings = {},
        showBiometricPrompt = {},
        setupDialog = {},
        isAnyDebuggingSettingEnabled = true
    )
}
