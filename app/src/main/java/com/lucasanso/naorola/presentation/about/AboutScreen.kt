package com.lucasanso.naorola.presentation.about

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lucasanso.naorola.NaoRolaApp
import com.lucasanso.naorola.R
import com.lucasanso.naorola.detection.rules.BrowserRules
import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.rules.YouTubeRules
import com.lucasanso.naorola.presentation.components.NavRow
import com.lucasanso.naorola.presentation.components.ScreenScaffold
import com.lucasanso.naorola.presentation.components.SectionCard

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenDiagnostics: () -> Unit) {
    val version = (LocalContext.current.applicationContext as NaoRolaApp).container.appVersion
    ScreenScaffold(title = stringResource(R.string.about_title), onBack = onBack) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_mark),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            }
            Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_philosophy), style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard(title = stringResource(R.string.about_privacy_title)) {
            Text(stringResource(R.string.about_privacy_body), style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard(title = stringResource(R.string.about_rules_title)) {
            listOf(YouTubeRules.INFO, InstagramRules.INFO, BrowserRules.INFO).forEach { info ->
                Text(
                    stringResource(R.string.about_rule_line, info.name, info.version, info.status),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            NavRow(stringResource(R.string.settings_diagnostics), stringResource(R.string.settings_diagnostics_summary), onOpenDiagnostics)
        }
        SectionCard(title = stringResource(R.string.about_limits_title)) {
            Text(stringResource(R.string.about_limits_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
