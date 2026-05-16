package com.prometheus.android.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.prometheus.android.service.LocationProvider
import com.prometheus.android.ui.shared.LoadingOverlay
import com.prometheus.android.ui.theme.PrometheusColors
import com.prometheus.model.DangerClassifier
import com.prometheus.model.EarthquakeEvent
import com.prometheus.model.UserLocation
import com.prometheus.network.EvacuationRouter
import com.prometheus.network.EvacuationRoute
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    event: EarthquakeEvent? = null,
    userLocation: UserLocation? = null
) {
    val context = LocalContext.current
    val locationProvider = remember { LocationProvider(context) }
    val currentLocation = remember {
        userLocation ?: locationProvider.getLastKnownLocation()
    }

    val epicenter: LatLng? = remember(event) {
        event?.coordinatePair?.let { LatLng(it.first, it.second) }
    }

    val userLatLng: LatLng? = remember(currentLocation) {
        currentLocation?.let { LatLng(it.latitude, it.longitude) }
    }

    val matches = remember(event, currentLocation) {
        if (event != null && currentLocation != null) {
            DangerClassifier.classify(event, currentLocation.latitude, currentLocation.longitude)
        } else event?.matchedDangerRules ?: emptyList()
    }

    val highestSev = remember(matches) {
        matches.maxByOrNull { it.severity.ordinal }
    }

    val dangerRadiusKm = remember(highestSev) {
        when (highestSev?.id) {
            "local_danger" -> 50.0
            "regional_major" -> 150.0
            "mega_earthquake" -> 400.0
            "deep_close" -> 100.0
            "tsunami_potential" -> 100.0
            else -> event?.magnitudeValue?.let { mag ->
                when {
                    mag >= 7.0f -> 400.0
                    mag >= 6.0f -> 150.0
                    mag >= 5.0f -> 50.0
                    else -> null
                }
            }
        }
    }

    val evacDirection = remember(epicenter, userLatLng, dangerRadiusKm) {
        if (epicenter != null && userLatLng != null && dangerRadiusKm != null) {
            val bearing = Math.toDegrees(
                atan2(
                    sin(Math.toRadians(userLatLng.longitude - epicenter.longitude)) * cos(Math.toRadians(userLatLng.latitude)),
                    cos(Math.toRadians(epicenter.latitude)) * sin(Math.toRadians(userLatLng.latitude)) -
                            sin(Math.toRadians(epicenter.latitude)) * cos(Math.toRadians(userLatLng.latitude)) * cos(Math.toRadians(userLatLng.longitude - epicenter.longitude))
                )
            )
            val dir = when {
                bearing in -22.5..22.5 -> "North"
                bearing in 22.5..67.5 -> "North-East"
                bearing in 67.5..112.5 -> "East"
                bearing in 112.5..157.5 -> "South-East"
                bearing in 157.5..180.0 || bearing in -180.0..-157.5 -> "South"
                bearing in -157.5..-112.5 -> "South-West"
                bearing in -112.5..-67.5 -> "West"
                else -> "North-West"
            }
            (bearing % 360 + 360) % 360 to dir
        } else null
    }

    var cameraPosition by remember {
        mutableStateOf(
            if (userLatLng != null) CameraPosition.fromLatLngZoom(userLatLng, 10f)
            else if (epicenter != null) CameraPosition.fromLatLngZoom(epicenter, 8f)
            else CameraPosition.fromLatLngZoom(LatLng(-2.5, 118.0), 5f)
        )
    }

    val cameraState = rememberCameraPositionState { position = cameraPosition }

    LaunchedEffect(epicenter, userLatLng) {
        if (epicenter != null && userLatLng != null) {
            val bounds = LatLngBounds.builder()
                .include(epicenter)
                .include(userLatLng)
                .build()
            cameraState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        } else if (epicenter != null) {
            cameraState.animate(CameraUpdateFactory.newLatLngZoom(epicenter, 8f))
        } else if (userLatLng != null) {
            cameraState.animate(CameraUpdateFactory.newLatLngZoom(userLatLng, 12f))
        }
    }

    val isDangerous = highestSev != null && (highestSev.severity.ordinal <= 1)

    var evacuationRoute by remember { mutableStateOf<EvacuationRoute?>(null) }
    var routeLoading by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    val googleApiKey = remember {
        try {
            val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            ai.metaData?.getString("com.google.android.directions.API_KEY") ?: ""
        } catch (_: Exception) { "" }
    }

    LaunchedEffect(epicenter, userLatLng, dangerRadiusKm) {
        evacuationRoute = null
        if (epicenter != null && userLatLng != null && dangerRadiusKm != null && isDangerous && googleApiKey.isNotEmpty()) {
            routeLoading = true
            val router = EvacuationRouter(googleApiKey)
            try {
                evacuationRoute = router.fetchEvacuationRoute(
                    userLat = userLatLng.latitude,
                    userLon = userLatLng.longitude,
                    epicenterLat = epicenter.latitude,
                    epicenterLon = epicenter.longitude,
                    dangerRadiusKm = dangerRadiusKm
                )
            } finally {
                router.close()
                routeLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Evacuation", color = PrometheusColors.blue) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrometheusColors.cardBackground
                ),
                actions = {
                    Text(
                        text = if (isDangerous) "\u26A0\uFE0F" else "\uD83D\uDDFA\uFE0F",
                        color = if (isDangerous) Color.Red else PrometheusColors.blue,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        },
        containerColor = PrometheusColors.darkBackground
    ) { padding ->
        val mapsAvailable = remember {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        }
        val apiKeyValid = remember {
            val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            val key = ai.metaData?.getString("com.google.android.geo.API_KEY")
            !key.isNullOrEmpty() && key != "YOUR_GOOGLE_MAPS_API_KEY_HERE"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            EvacuationStatusBanner(isDangerous = isDangerous, severity = highestSev?.severity)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
            ) {
                LoadingOverlay(isLoading = routeLoading) {
                if (mapsAvailable == ConnectionResult.SUCCESS && apiKeyValid) {
                    val hasLocationPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    GoogleMap(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f)),
                        cameraPositionState = cameraState,
                        properties = MapProperties(
                            isMyLocationEnabled = hasLocationPermission,
                            mapType = MapType.NORMAL
                        ),
                        uiSettings = MapUiSettings(
                            myLocationButtonEnabled = true,
                            zoomControlsEnabled = true
                        ),
                        onMapClick = { /* no-op */ }
                    ) {
                        if (epicenter != null) {
                            Marker(
                                state = MarkerState(position = epicenter),
                                title = "Epicenter",
                                snippet = event?._wilayah ?: "",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                            )
                        }

                        if (userLatLng != null) {
                            Marker(
                                state = MarkerState(position = userLatLng),
                                title = "You",
                                snippet = "Current location",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                            )
                        }

                        if (epicenter != null && dangerRadiusKm != null) {
                            val radiusMeters = dangerRadiusKm * 1000.0
                            Circle(
                                center = epicenter,
                                radius = radiusMeters,
                                fillColor = Color.Red.copy(alpha = 0.15f),
                                strokeColor = Color.Red.copy(alpha = 0.5f),
                                strokeWidth = 3f
                            )
                        }

                        if (evacuationRoute != null) {
                            Polyline(
                                points = evacuationRoute!!.coordinates.map { LatLng(it.first, it.second) },
                                color = PrometheusColors.blue,
                                width = 6f
                            )
                        }
                    }
                } else {
                    MapUnavailableFallback(epicenter)
                }
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PrometheusColors.cardBackground)
                            .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f))
                            .clickable { showDetails = !showDetails }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ROUTING DETAILS",
                                color = PrometheusColors.blue,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (showDetails) "\u25BC" else "\u25B2",
                                color = PrometheusColors.blue,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showDetails,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        RoutingDetailsCard(
                            event = event,
                            userLocation = currentLocation,
                            isDangerous = isDangerous,
                            dangerRadiusKm = dangerRadiusKm,
                            evacDirection = evacDirection,
                            ruleName = highestSev?.ruleName,
                            evacuationRoute = evacuationRoute,
                            routeLoading = routeLoading
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EvacuationStatusBanner(isDangerous: Boolean, severity: com.prometheus.model.DangerSeverity?) {
    val targetColor = when {
        isDangerous -> PrometheusColors.danger
        severity == com.prometheus.model.DangerSeverity.MEDIUM -> PrometheusColors.warning
        else -> PrometheusColors.blue
    }
    val color by animateColorAsState(targetColor, animationSpec = tween(400), label = "banner_color")
    val label = when {
        isDangerous -> "EVACUATION ROUTING — ACTIVE"
        severity == com.prometheus.model.DangerSeverity.MEDIUM -> "MEDIUM ALERT — MONITOR"
        else -> "EVACUATION ROUTING — STANDBY"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isDangerous) "\u26A0\uFE0F" else "\uD83D\uDEE1\uFE0F",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RoutingDetailsCard(
    event: EarthquakeEvent?,
    userLocation: UserLocation?,
    isDangerous: Boolean,
    dangerRadiusKm: Double?,
    evacDirection: Pair<Double, String>?,
    ruleName: String?,
    evacuationRoute: EvacuationRoute?,
    routeLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(PrometheusColors.cardBackground)
            .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        val epicenterStr = buildString {
            val loc = event?._wilayah ?: "No event"
            append(loc)
            event?.coordinatePair?.let {
                append(" (${"%.2f".format(it.first)}, ${"%.2f".format(it.second)})")
            }
        }
        val userLocStr = if (userLocation != null) {
            "${"%.4f".format(userLocation.latitude)}, ${"%.4f".format(userLocation.longitude)}"
        } else "Not acquired"

        val ruleNameStr = ruleName ?: "No active event"
        val radiusStr = dangerRadiusKm?.let { "${it.toInt()} km" } ?: "--"
        val dirStr = if (isDangerous && evacDirection != null) {
            "Evacuate ${evacDirection.second} (${"%.0f".format(evacDirection.first)}° from epicenter)"
        } else if (isDangerous) "Calculating..." else "No active event"
        val magStr = event?.magnitudeValue?.let { "M ${"%.1f".format(it)}" } ?: "--"
        val depthStr = event?._kedalaman ?: "--"

        RouteInfoRow(label = "RULE", value = ruleNameStr)
        HorizontalDivider(color = PrometheusColors.blue.copy(alpha = 0.15f))
        RouteInfoRow(label = "MAGNITUDE", value = magStr)
        HorizontalDivider(color = PrometheusColors.blue.copy(alpha = 0.15f))
        RouteInfoRow(label = "USER LOCATION", value = userLocStr)
        HorizontalDivider(color = PrometheusColors.blue.copy(alpha = 0.15f))
        RouteInfoRow(label = "DANGER RADIUS", value = radiusStr)
        HorizontalDivider(color = PrometheusColors.blue.copy(alpha = 0.15f))
        RouteInfoRow(label = "DIRECTION", value = dirStr)
        HorizontalDivider(color = PrometheusColors.blue.copy(alpha = 0.15f))
        RouteInfoRow(label = "DISTANCE", value = if (evacuationRoute != null) "${"%.1f".format(evacuationRoute.distanceKm)} km" else if (isDangerous && !routeLoading) "Unavailable" else "--")
        if (evacuationRoute != null) {
            HorizontalDivider(color = PrometheusColors.blue.copy(alpha = 0.15f))
            Text(
                text = "ESTIMATED TIME",
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            TransportTimeRow(icon = "\uD83D\uDEB6", label = "Walk", minutes = evacuationRoute.walkMin)
            TransportTimeRow(icon = "\uD83C\uDFC3", label = "Run", minutes = evacuationRoute.runMin)
            TransportTimeRow(icon = "\uD83D\uDEB2", label = "Cycle", minutes = evacuationRoute.cycleMin)
            TransportTimeRow(icon = "\uD83D\uDEF4", label = "Motor", minutes = evacuationRoute.motorMin)
            TransportTimeRow(icon = "\uD83D\uDE97", label = "Car", minutes = evacuationRoute.durationMin)
        }
    }
}

@Composable
private fun MapUnavailableFallback(epicenter: LatLng?) {
    val context = LocalContext.current
    val isApiKeyMissing = remember {
        try {
            val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            val key = ai.metaData?.getString("com.google.android.geo.API_KEY")
            key.isNullOrEmpty() || key == "YOUR_GOOGLE_MAPS_API_KEY_HERE"
        } catch (_: Exception) { true }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrometheusColors.cardBackground)
            .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "\uD83D\uDDFA\uFE0F",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Maps Unavailable",
                color = PrometheusColors.blue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            if (isApiKeyMissing) {
                Text(
                    text = "Set a Google Maps API key in AndroidManifest.xml:\n" +
                            "<meta-data android:name=\"com.google.android.geo.API_KEY\"\n" +
                            "  android:value=\"YOUR_KEY\"/>",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "Google Play Services may be missing.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
            if (epicenter != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Epicenter: ${"%.2f".format(epicenter.latitude)}, ${"%.2f".format(epicenter.longitude)}",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun TransportTimeRow(icon: String, label: String, minutes: Double) {
    val display = when {
        minutes < 1.0 -> "< 1 min"
        minutes < 60.0 -> "${"%.0f".format(minutes)} min"
        else -> {
            val h = (minutes / 60).toInt()
            val m = (minutes % 60).toInt()
            "${h}h ${m}m"
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(56.dp)
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = display,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RouteInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(128.dp)
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
