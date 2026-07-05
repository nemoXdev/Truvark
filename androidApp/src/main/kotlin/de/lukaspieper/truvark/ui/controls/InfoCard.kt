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
import de.lukaspieper.truvark.ui.theme.paddings

@Composable
public fun InfoCard(
    infoCardState: InfoCardState,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = infoCardState.containerColor),
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = spacedBy(MaterialTheme.paddings.medium),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(MaterialTheme.paddings.large)
        ) {
            ShapedIcon(
                imageVector = infoCardState.icon,
                tint = infoCardState.contentColor
            )

            Column(
                verticalArrangement = spacedBy(MaterialTheme.paddings.small)
            ) {
                Text(
                    text = stringResource(infoCardState.title),
                    style = MaterialTheme.typography.titleSmall,
                    color = infoCardState.contentColor
                )
                Text(
                    text = stringResource(infoCardState.description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = infoCardState.contentColor
                )
            }
        }
    }
}

@Immutable
public data class InfoCardState(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color
)
