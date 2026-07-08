/*
 * SPDX-FileCopyrightText: 2026 Lukas Pieper
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.lukaspieper.truvark.ui.controls

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.lukaspieper.truvark.ui.theme.paddings

@Composable
public fun InfoCard(
    state: InfoCardState,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = state.containerColor),
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = spacedBy(MaterialTheme.paddings.medium),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(MaterialTheme.paddings.large)
        ) {
            ShapedIcon(
                imageVector = state.icon,
                size = state.iconSize,
                tint = state.contentColor
            )

            Column(
                verticalArrangement = spacedBy(MaterialTheme.paddings.small)
            ) {
                Text(
                    text = stringResource(state.title),
                    style = MaterialTheme.typography.titleSmall,
                    color = state.contentColor
                )

                if (state.description != null) {
                    Text(
                        text = stringResource(state.description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = state.contentColor
                    )
                }
            }
        }
    }
}

@Immutable
public data class InfoCardState(
    @StringRes val title: Int,
    @StringRes val description: Int? = null,
    val icon: ImageVector,
    val iconSize: Dp = 48.dp,
    val containerColor: Color,
    val contentColor: Color
)
