package com.prometheus.android.ui.map

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Motorcycle
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Public
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.prometheus.android.R
import com.prometheus.android.service.LocationProvider
import com.prometheus.android.ui.shared.LoadingOverlay
import com.prometheus.android.ui.theme.LocalPrometheusColors
import com.prometheus.model.DangerClassifier
import com.prometheus.model.EarthquakeEvent
import com.prometheus.model.UserLocation
import com.prometheus.network.EvacuationRouter
import com.prometheus.network.EvacuationRoute
import kotlin.math.*

private fun scaledMarkerIcon(context: android.content.Context, drawableRes: Int, targetDp: Int): com.google.android.gms.maps.model.BitmapDescriptor {
    val opts = BitmapFactory.Options().apply { inScaled = false }
    val bitmap = BitmapFactory.decodeResource(context.resources, drawableRes, opts) ?: return BitmapDescriptorFactory.defaultMarker()
    val density = context.resources.displayMetrics.density
    val targetPx = (targetDp * density).toInt()
    val scaled = Bitmap.createScaledBitmap(bitmap, targetPx, targetPx, true)
    if (scaled !== bitmap) bitmap.recycle()
    return BitmapDescriptorFactory.fromBitmap(scaled)
}

private val PaleCyan = Color(0xFFE0F2F9)
private val PaleCyanBorder = Color(0xFFB3E0F2)
private val BrightBlue = Color(0xFF00BFFF)
private val PaleGreen = Color(0xFFE5F4E3)
private val PaleGreenBorder = Color(0xFFC2E5BD)
private val DarkGreen = Color(0xFF2E7D32)
private val CardWhite = Color(0xFFFFFFFF)
private val CardBorder = Color(0xFFE0E0E0)
private val BodyTextColor = Color(0xFF555555)

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
    val p = LocalPrometheusColors.current

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
    val scrollState = rememberScrollState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied */ }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

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
                title = { Text("Evacuation", color = p.blue) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = p.surface
                )
            )
        },
        containerColor = p.background
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
                .verticalScroll(scrollState)
        ) {
            TopStatusBanner(isDangerous = isDangerous, severity = highestSev?.severity)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                LoadingOverlay(isLoading = routeLoading) {
                    if (mapsAvailable == ConnectionResult.SUCCESS && apiKeyValid) {
                        val hasLocationPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        val epicenterIcon = remember { scaledMarkerIcon(context, R.drawable.earthquake, 48) }
                        val userIcon = remember { scaledMarkerIcon(context, R.drawable.location, 40) }

                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraState,
                            properties = MapProperties(
                                isMyLocationEnabled = hasLocationPermission,
                                mapType = MapType.NORMAL
                            ),
                            uiSettings = MapUiSettings(
                                myLocationButtonEnabled = true,
                                zoomControlsEnabled = true
                            ),
                            onMapClick = { }
                        ) {
                            if (epicenter != null) {
                                Marker(
                                    state = MarkerState(position = epicenter),
                                    title = "Epicenter",
                                    snippet = event?._wilayah ?: "",
                                    icon = epicenterIcon
                                )
                            }
                            if (userLatLng != null) {
                                Marker(
                                    state = MarkerState(position = userLatLng),
                                    title = "You",
                                    snippet = "Current location",
                                    icon = userIcon
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
                                    color = BrightBlue,
                                    width = 6f
                                )
                            }
                        }
                    } else {
                        MapUnavailableFallback(epicenter)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (evacuationRoute != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(BrightBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NearMe,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        label = "VIEW ROUTE",
                        bgColor = PaleCyan,
                        borderColor = PaleCyanBorder,
                        labelColor = BrightBlue,
                        onClick = { showDetails = !showDetails }
                    )
                    val exitLat = evacuationRoute!!.coordinates.last().first
                    val exitLon = evacuationRoute!!.coordinates.last().second
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Map,
                                contentDescription = null,
                                tint = DarkGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = "Maps",
                        bgColor = PaleGreen,
                        borderColor = PaleGreenBorder,
                        labelColor = DarkGreen,
                        onClick = {
                            val gmmIntentUri = Uri.parse("google.navigation:q=$exitLat,$exitLon")
                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                            mapIntent.setPackage("com.google.android.apps.maps")
                            if (mapIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(mapIntent)
                            } else {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$exitLat,$exitLon"))
                                context.startActivity(webIntent)
                            }
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))
            }

            Box(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.animateContentSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PaleCyan)
                            .border(1.dp, PaleCyanBorder, RoundedCornerShape(12.dp))
                            .clickable { showDetails = !showDetails }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ROUTING DETAILS",
                                color = BrightBlue,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                imageVector = if (showDetails) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (showDetails) "Collapse" else "Expand",
                                tint = BrightBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showDetails,
                        enter = slideInVertically(
                            initialOffsetY = { -it },
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(350)),
                        exit = slideOutVertically(
                            targetOffsetY = { -it },
                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(250))
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

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                HowItWorksCard()
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TopStatusBanner(isDangerous: Boolean, severity: com.prometheus.model.DangerSeverity?) {
    val label = when {
        isDangerous -> "EVACUATION ROUTING \u2014 ACTIVE"
        severity == com.prometheus.model.DangerSeverity.MEDIUM -> "MEDIUM ALERT \u2014 MONITOR"
        else -> "EVACUATION ROUTING \u2014 STANDBY"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(PaleCyan)
            .border(1.dp, PaleCyanBorder, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(BrightBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.NearMe,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                color = BrightBlue,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
    bgColor: Color,
    borderColor: Color,
    labelColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = labelColor
            )
        }
    }
}

@Composable
private fun HowItWorksCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardWhite)
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "HOW IT WORKS",
            style = MaterialTheme.typography.labelMedium,
            color = BrightBlue,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "On a dangerous event, the BMKG epicentre is shown on the map with a danger radius. " +
                    "The blue route line shows the fastest road exit from the danger zone via Google Directions. " +
                    "Follow official BMKG and BNPB instructions.",
            style = MaterialTheme.typography.bodySmall,
            color = BodyTextColor,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight
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
    val p = LocalPrometheusColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardWhite)
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
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
        } else if (isDangerous && routeLoading) "Calculating..." else if (isDangerous) "Pending..." else "No active event"
        val magStr = event?.magnitudeValue?.let { "M ${"%.1f".format(it)}" } ?: "--"

        RouteInfoRow(label = "RULE", value = ruleNameStr)
        HorizontalDivider(color = p.surfaceElevated)
        RouteInfoRow(label = "MAGNITUDE", value = magStr)
        HorizontalDivider(color = p.surfaceElevated)
        RouteInfoRow(label = "USER LOCATION", value = userLocStr)
        HorizontalDivider(color = p.surfaceElevated)
        RouteInfoRow(label = "DANGER RADIUS", value = radiusStr)
        HorizontalDivider(color = p.surfaceElevated)
        RouteInfoRow(label = "DIRECTION", value = dirStr)
        HorizontalDivider(color = p.surfaceElevated)
        RouteInfoRow(label = "DISTANCE", value = if (evacuationRoute != null) "${"%.1f".format(evacuationRoute.distanceKm)} km" else if (isDangerous && !routeLoading) "Unavailable" else "--")

        if (evacuationRoute != null) {
            HorizontalDivider(color = p.surfaceElevated)
            Text(
                text = "ESTIMATED TIME",
                color = p.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            TransportTimeRow(icon = { Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = "Walk", modifier = Modifier.size(20.dp)) }, label = "Walk", minutes = evacuationRoute.walkMin)
            TransportTimeRow(icon = { Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = "Run", modifier = Modifier.size(20.dp)) }, label = "Run", minutes = evacuationRoute.runMin)
            TransportTimeRow(icon = { Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = "Cycle", modifier = Modifier.size(20.dp)) }, label = "Cycle", minutes = evacuationRoute.cycleMin)
            TransportTimeRow(icon = { Icon(Icons.Filled.Motorcycle, contentDescription = "Motor", modifier = Modifier.size(20.dp)) }, label = "Motor", minutes = evacuationRoute.motorMin)
            TransportTimeRow(icon = { Icon(Icons.Filled.DirectionsCar, contentDescription = "Car", modifier = Modifier.size(20.dp)) }, label = "Car", minutes = evacuationRoute.durationMin)

            if (evacuationRoute.steps.isNotEmpty()) {
                HorizontalDivider(color = p.surfaceElevated, modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "TURN BY TURN",
                    color = p.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                evacuationRoute.steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${index + 1}.",
                            color = p.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapUnavailableFallback(epicenter: LatLng?) {
    val p = LocalPrometheusColors.current
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
            .background(p.surface)
            .border(1.dp, p.blue.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = "Map",
                modifier = Modifier.size(48.dp),
                tint = p.blue
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Maps Unavailable",
                color = p.blue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            if (isApiKeyMissing) {
                Text(
                    text = "Set a Google Maps API key in AndroidManifest.xml:\n" +
                            "<meta-data android:name=\"com.google.android.geo.API_KEY\"\n" +
                            "  android:value=\"YOUR_KEY\"/>",
                    color = p.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "Google Play Services may be missing.",
                    color = p.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
            if (epicenter != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Epicenter: ${"%.2f".format(epicenter.latitude)}, ${"%.2f".format(epicenter.longitude)}",
                    color = p.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun TransportTimeRow(icon: @Composable () -> Unit, label: String, minutes: Double) {
    val p = LocalPrometheusColors.current
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = p.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(48.dp)
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = display,
            color = p.textPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RouteInfoRow(label: String, value: String) {
    val p = LocalPrometheusColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = p.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            color = p.textPrimary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
