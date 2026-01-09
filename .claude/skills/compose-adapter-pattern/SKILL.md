---
name: compose-adapter-pattern
description: Implements adapter pattern for data transformation between layers. Use when converting data models for display or integrating external data sources.
---

# Compose Adapter Pattern

## Overview

The Adapter pattern converts data from one format to another, enabling incompatible interfaces to work together. In FrameLapse, this is essential for transforming domain entities into presentation models, converting API responses, and formatting data for display.

## When to Use

- Converting domain entities to UI display models
- Transforming API/external data to domain entities
- Formatting data for specific UI requirements
- Adapting third-party data structures

## 1. Basic Adapter Pattern

### Data Classes

```kotlin
// Source: Domain entity
data class Frame(
    val id: String,
    val projectId: String,
    val originalPath: String,
    val alignedPath: String?,
    val timestamp: Long,
    val confidence: Float?,
    val sortOrder: Int,
)

// Target: UI presentation model
data class FrameDisplayItem(
    val id: String,
    val thumbnailPath: String,
    val frameNumber: String,
    val captureTime: String,
    val alignmentStatus: AlignmentStatus,
    val qualityIndicator: String?,
)

enum class AlignmentStatus {
    NOT_ALIGNED,
    ALIGNED,
    LOW_CONFIDENCE,
}
```

### Adapter Implementation

```kotlin
// presentation/adapter/FrameDisplayAdapter.kt

class FrameDisplayAdapter {

    private val dateFormatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    fun adapt(frame: Frame): FrameDisplayItem {
        return FrameDisplayItem(
            id = frame.id,
            thumbnailPath = frame.alignedPath ?: frame.originalPath,
            frameNumber = "Frame ${frame.sortOrder + 1}",
            captureTime = formatTimestamp(frame.timestamp),
            alignmentStatus = determineAlignmentStatus(frame),
            qualityIndicator = formatConfidence(frame.confidence),
        )
    }

    fun adaptList(frames: List<Frame>): List<FrameDisplayItem> {
        return frames.map { adapt(it) }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return dateFormatter.format(Date(timestamp))
    }

    private fun determineAlignmentStatus(frame: Frame): AlignmentStatus {
        return when {
            frame.alignedPath == null -> AlignmentStatus.NOT_ALIGNED
            frame.confidence != null && frame.confidence < 0.7f -> AlignmentStatus.LOW_CONFIDENCE
            else -> AlignmentStatus.ALIGNED
        }
    }

    private fun formatConfidence(confidence: Float?): String? {
        return confidence?.let { "${(it * 100).toInt()}%" }
    }
}
```

### Usage in ViewModel

```kotlin
// presentation/gallery/GalleryViewModel.kt

class GalleryViewModel(
    private val getFramesUseCase: GetFramesUseCase,
    private val frameAdapter: FrameDisplayAdapter = FrameDisplayAdapter(),
) : BaseViewModel<GalleryState, GalleryEvent, GalleryEffect>(GalleryState()) {

    private fun loadFrames(projectId: String) {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }

            getFramesUseCase(projectId)
                .onSuccess { frames ->
                    // Adapt domain entities to display items
                    val displayItems = frameAdapter.adaptList(frames)
                    updateState {
                        copy(
                            isLoading = false,
                            frames = displayItems,
                        )
                    }
                }
                .onError { _, message ->
                    updateState { copy(isLoading = false, error = message) }
                }
        }
    }
}
```

## 2. Weather Data Adapter (External API)

### API Response to Domain

```kotlin
// data/remote/model/WeatherApiResponse.kt
data class WeatherApiResponse(
    val main: MainData,
    val weather: List<WeatherCondition>,
    val wind: WindData,
    val name: String,
)

data class MainData(
    val temp: Double,
    val humidity: Int,
)

data class WeatherCondition(
    val id: Int,
    val main: String,
    val description: String,
)

data class WindData(
    val speed: Double,
)

// domain/entity/Weather.kt
data class Weather(
    val temperature: Int,
    val humidity: Int,
    val windSpeed: Double,
    val condition: WeatherConditionType,
    val locationName: String,
)

enum class WeatherConditionType {
    SUNNY, CLOUDY, RAINY, SNOWY, STORMY, UNKNOWN
}

// data/adapter/WeatherApiAdapter.kt
class WeatherApiAdapter {

    fun adapt(response: WeatherApiResponse): Weather {
        return Weather(
            temperature = response.main.temp.roundToInt(),
            humidity = response.main.humidity,
            windSpeed = response.wind.speed,
            condition = mapCondition(response.weather.firstOrNull()?.id),
            locationName = response.name,
        )
    }

    private fun mapCondition(conditionId: Int?): WeatherConditionType {
        return when (conditionId) {
            in 200..299 -> WeatherConditionType.STORMY
            in 300..399 -> WeatherConditionType.RAINY
            in 500..599 -> WeatherConditionType.RAINY
            in 600..699 -> WeatherConditionType.SNOWY
            in 700..799 -> WeatherConditionType.CLOUDY
            800 -> WeatherConditionType.SUNNY
            in 801..899 -> WeatherConditionType.CLOUDY
            else -> WeatherConditionType.UNKNOWN
        }
    }
}
```

### Weather Display Adapter

```kotlin
// presentation/adapter/WeatherDisplayAdapter.kt

data class WeatherDisplayItem(
    val temperatureDisplay: String,
    val humidityDisplay: String,
    val windDisplay: String,
    val conditionIcon: ImageVector,
    val conditionDescription: String,
    val location: String,
)

class WeatherDisplayAdapter {

    fun adapt(weather: Weather): WeatherDisplayItem {
        return WeatherDisplayItem(
            temperatureDisplay = "${weather.temperature}°C",
            humidityDisplay = "${weather.humidity}%",
            windDisplay = "${weather.windSpeed} m/s",
            conditionIcon = getConditionIcon(weather.condition),
            conditionDescription = getConditionDescription(weather.condition),
            location = weather.locationName,
        )
    }

    private fun getConditionIcon(condition: WeatherConditionType): ImageVector {
        return when (condition) {
            WeatherConditionType.SUNNY -> Icons.Default.WbSunny
            WeatherConditionType.CLOUDY -> Icons.Default.Cloud
            WeatherConditionType.RAINY -> Icons.Default.Grain
            WeatherConditionType.SNOWY -> Icons.Default.AcUnit
            WeatherConditionType.STORMY -> Icons.Default.Thunderstorm
            WeatherConditionType.UNKNOWN -> Icons.Default.HelpOutline
        }
    }

    private fun getConditionDescription(condition: WeatherConditionType): String {
        return when (condition) {
            WeatherConditionType.SUNNY -> "Clear skies"
            WeatherConditionType.CLOUDY -> "Cloudy"
            WeatherConditionType.RAINY -> "Rainy"
            WeatherConditionType.SNOWY -> "Snow"
            WeatherConditionType.STORMY -> "Thunderstorm"
            WeatherConditionType.UNKNOWN -> "Unknown"
        }
    }
}
```

## 3. Project Statistics Adapter

### Aggregating Data for Dashboard

```kotlin
// domain/entity/Project.kt
data class Project(
    val id: String,
    val name: String,
    val createdAt: Long,
    val frameCount: Int = 0,
)

// presentation/model/ProjectCardItem.kt
data class ProjectCardItem(
    val id: String,
    val name: String,
    val frameCountDisplay: String,
    val createdDisplay: String,
    val thumbnailPath: String?,
    val progressPercent: Float,
    val statusChip: StatusChip,
)

data class StatusChip(
    val text: String,
    val color: Color,
)

// presentation/adapter/ProjectCardAdapter.kt
class ProjectCardAdapter(
    private val frameRepository: FrameRepository,
) {
    suspend fun adapt(project: Project): ProjectCardItem {
        val frames = frameRepository.getFramesByProject(project.id)
            .getOrNull() ?: emptyList()

        val alignedCount = frames.count { it.alignedPath != null }
        val thumbnailPath = frames.firstOrNull()?.let { it.alignedPath ?: it.originalPath }

        return ProjectCardItem(
            id = project.id,
            name = project.name,
            frameCountDisplay = formatFrameCount(frames.size),
            createdDisplay = formatCreatedDate(project.createdAt),
            thumbnailPath = thumbnailPath,
            progressPercent = if (frames.isNotEmpty()) {
                alignedCount.toFloat() / frames.size
            } else {
                0f
            },
            statusChip = determineStatus(frames.size, alignedCount),
        )
    }

    suspend fun adaptList(projects: List<Project>): List<ProjectCardItem> {
        return projects.map { adapt(it) }
    }

    private fun formatFrameCount(count: Int): String {
        return when (count) {
            0 -> "No frames"
            1 -> "1 frame"
            else -> "$count frames"
        }
    }

    private fun formatCreatedDate(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            diff < 604800_000 -> "${diff / 86400_000}d ago"
            else -> SimpleDateFormat("MMM dd", Locale.getDefault())
                .format(Date(timestamp))
        }
    }

    private fun determineStatus(total: Int, aligned: Int): StatusChip {
        return when {
            total == 0 -> StatusChip("Empty", Color.Gray)
            aligned == total -> StatusChip("Ready", Color.Green)
            aligned > 0 -> StatusChip("Processing", Color.Orange)
            else -> StatusChip("New", Color.Blue)
        }
    }
}
```

## 4. Export Settings Adapter

### Form Data to Domain

```kotlin
// presentation/model/ExportFormData.kt
data class ExportFormData(
    val selectedResolution: String,
    val selectedFps: String,
    val selectedFormat: String,
    val includeAudio: Boolean,
    val quality: Float,
)

// domain/entity/ExportSettings.kt
data class ExportSettings(
    val resolution: Resolution,
    val fps: Int,
    val format: VideoFormat,
    val includeAudio: Boolean,
    val quality: Quality,
)

enum class Resolution(val width: Int, val height: Int) {
    HD(1280, 720),
    FULL_HD(1920, 1080),
    UHD_4K(3840, 2160),
}

enum class VideoFormat(val extension: String) {
    MP4("mp4"),
    MOV("mov"),
    WEBM("webm"),
}

enum class Quality {
    LOW, MEDIUM, HIGH, MAXIMUM
}

// presentation/adapter/ExportSettingsAdapter.kt
class ExportSettingsAdapter {

    fun adapt(formData: ExportFormData): ExportSettings {
        return ExportSettings(
            resolution = parseResolution(formData.selectedResolution),
            fps = parseFps(formData.selectedFps),
            format = parseFormat(formData.selectedFormat),
            includeAudio = formData.includeAudio,
            quality = parseQuality(formData.quality),
        )
    }

    fun toFormData(settings: ExportSettings): ExportFormData {
        return ExportFormData(
            selectedResolution = formatResolution(settings.resolution),
            selectedFps = "${settings.fps} fps",
            selectedFormat = settings.format.name,
            includeAudio = settings.includeAudio,
            quality = qualityToFloat(settings.quality),
        )
    }

    private fun parseResolution(value: String): Resolution {
        return when {
            value.contains("4K") -> Resolution.UHD_4K
            value.contains("1080") -> Resolution.FULL_HD
            else -> Resolution.HD
        }
    }

    private fun parseFps(value: String): Int {
        return value.filter { it.isDigit() }.toIntOrNull() ?: 30
    }

    private fun parseFormat(value: String): VideoFormat {
        return VideoFormat.entries.find {
            it.name.equals(value, ignoreCase = true)
        } ?: VideoFormat.MP4
    }

    private fun parseQuality(slider: Float): Quality {
        return when {
            slider < 0.25f -> Quality.LOW
            slider < 0.5f -> Quality.MEDIUM
            slider < 0.75f -> Quality.HIGH
            else -> Quality.MAXIMUM
        }
    }

    private fun formatResolution(resolution: Resolution): String {
        return "${resolution.height}p (${resolution.width}x${resolution.height})"
    }

    private fun qualityToFloat(quality: Quality): Float {
        return when (quality) {
            Quality.LOW -> 0.125f
            Quality.MEDIUM -> 0.375f
            Quality.HIGH -> 0.625f
            Quality.MAXIMUM -> 0.875f
        }
    }
}
```

## 5. SQLDelight Entity Adapter (Data Layer)

### Database Entity to Domain

```kotlin
// data/mapper/FrameMapper.kt
// (Already exists in FrameLapse - reference pattern)

object FrameMapper {

    fun toDomain(entity: FrameEntity): Frame {
        return Frame(
            id = entity.id,
            projectId = entity.project_id,
            originalPath = entity.original_path,
            alignedPath = entity.aligned_path,
            timestamp = entity.timestamp,
            confidence = entity.confidence?.toFloat(),
            sortOrder = entity.sort_order.toInt(),
        )
    }

    fun toEntity(domain: Frame): FrameEntity {
        return FrameEntity(
            id = domain.id,
            project_id = domain.projectId,
            original_path = domain.originalPath,
            aligned_path = domain.alignedPath,
            timestamp = domain.timestamp,
            confidence = domain.confidence?.toDouble(),
            sort_order = domain.sortOrder.toLong(),
        )
    }

    fun toInsertParams(frame: Frame): FrameInsertParams {
        return FrameInsertParams(
            id = frame.id,
            project_id = frame.projectId,
            original_path = frame.originalPath,
            timestamp = frame.timestamp,
            sort_order = frame.sortOrder.toLong(),
        )
    }
}
```

## 6. Composable Adapter Display

### Using Adapted Data in UI

```kotlin
@Composable
fun FrameGridItem(
    item: FrameDisplayItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = item.thumbnailPath,
                    contentDescription = item.frameNumber,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Crop,
                )

                // Alignment status badge
                AlignmentStatusBadge(
                    status = item.alignmentStatus,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                )
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.frameNumber,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = item.captureTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.qualityIndicator?.let { quality ->
                    Text(
                        text = "Quality: $quality",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlignmentStatusBadge(
    status: AlignmentStatus,
    modifier: Modifier = Modifier,
) {
    val (icon, color) = when (status) {
        AlignmentStatus.ALIGNED -> Icons.Default.Check to Color.Green
        AlignmentStatus.LOW_CONFIDENCE -> Icons.Default.Warning to Color.Orange
        AlignmentStatus.NOT_ALIGNED -> Icons.Default.HourglassEmpty to Color.Gray
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.8f),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(4.dp)
                .size(16.dp),
            tint = Color.White,
        )
    }
}
```

## Anti-Patterns

### Avoid: Adapter with Business Logic

```kotlin
// BAD - adapter contains business logic
class BadFrameAdapter {
    fun adapt(frame: Frame): FrameDisplayItem {
        // Business logic should be in use case
        if (frame.confidence != null && frame.confidence < 0.5f) {
            triggerReAlignment(frame)  // Side effect!
        }
        // ...
    }
}

// BETTER - pure transformation only
class GoodFrameAdapter {
    fun adapt(frame: Frame): FrameDisplayItem {
        // Only data transformation, no side effects
        return FrameDisplayItem(
            // ...
        )
    }
}
```

### Avoid: Adapter in Composable

```kotlin
// BAD - creates adapter on every recomposition
@Composable
fun FrameList(frames: List<Frame>) {
    val adapter = FrameDisplayAdapter()  // Created every time!
    val items = adapter.adaptList(frames)  // Transformed every time!

    LazyColumn {
        items(items) { item -> ... }
    }
}

// BETTER - adapt in ViewModel, remember in composable
@Composable
fun FrameList(items: List<FrameDisplayItem>) {  // Already adapted
    LazyColumn {
        items(items) { item -> ... }
    }
}
```

## Reference Examples

- Existing mapper: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/mapper/FrameMapper.kt`
- Repository pattern: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/repository/FrameRepositoryImpl.kt`

## Checklist

### Adapter Design
- [ ] Pure transformation (no side effects)
- [ ] Handles null/missing data gracefully
- [ ] Provides both single and list adaptation
- [ ] Formats data for display (dates, numbers, etc.)

### Data Layer Adapters
- [ ] Domain ↔ Entity conversion
- [ ] Handles type differences (Long ↔ Int, Double ↔ Float)
- [ ] Located in data/mapper or data/adapter

### Presentation Layer Adapters
- [ ] Domain → Display model conversion
- [ ] Includes formatting for UI
- [ ] Calculates derived display properties
- [ ] Located in presentation/adapter

### Usage
- [ ] Adapters instantiated in ViewModel (not composable)
- [ ] Adaptation happens before state update
- [ ] Display models are simple data holders
