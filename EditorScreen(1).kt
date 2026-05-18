package com.agon.app.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import com.agon.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class CubeItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val lut: CubeLut,
    var intensity: Float = 1f
)

data class OverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val isGif: Boolean = false,
    var offset: Offset = Offset.Zero,
    var scale: Float = 1f,
    var zIndex: Int = 100
)

data class ImageItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val originalBitmap: Bitmap,
    val previewBitmap: Bitmap,
    val pixelsBuffer: IntArray,
    var processedBitmap: Bitmap? = null,
    var offset: Offset = Offset.Zero,
    var scale: Float = 1f,
    var zIndex: Int = 0
)

data class EditorState(
    val cubes: List<CubeItem>,
    val selectiveColors: List<SelectiveColor>,
    val adjustments: Adjustments,
    val engineMode: EngineMode,
    val matchSourceStats: FloatArray?,
    val matchTargetStats: FloatArray?,
    val matchLum: Float,
    val matchIntensity: Float,
    val matchFade: Float,
    val toning: TonalAdjustments,
    val curves: CurveSliders,
    val hslAdjustments: com.agon.app.data.HslAdjustments,
    val colorWheels: com.agon.app.data.ColorWheels,
    val advancedCurves: com.agon.app.data.AdvancedCurves,
    val colorBalance: com.agon.app.data.ColorBalance,
    val channelMixer: com.agon.app.data.ChannelMixer,
    val cinematicEffects: com.agon.app.data.CinematicEffects
)

@Composable
fun IOSVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White
) {
    var dragHeight by remember { mutableStateOf(0f) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x33FFFFFF))
            .onSizeChanged { dragHeight = it.height.toFloat() }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    val y = change.position.y.coerceIn(0f, dragHeight)
                    val newValue = 1f - (y / dragHeight)
                    onValueChange(newValue)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val y = offset.y.coerceIn(0f, dragHeight)
                    val newValue = 1f - (y / dragHeight)
                    onValueChange(newValue)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(value)
                .align(Alignment.BottomCenter)
                .background(activeColor, RoundedCornerShape(12.dp))
        )
    }
}

@Composable
fun IOSHorizontalSlider(
    value: Float,
    min: Float,
    max: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragWidth by remember { mutableStateOf(0f) }
    val normalizedValue = ((value - min) / (max - min)).coerceIn(0f, 1f)
    
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x33FFFFFF))
            .onSizeChanged { dragWidth = it.width.toFloat() }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val x = change.position.x.coerceIn(0f, dragWidth)
                    val percent = x / dragWidth
                    onValueChange(min + percent * (max - min))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val x = offset.x.coerceIn(0f, dragWidth)
                    val percent = x / dragWidth
                    onValueChange(min + percent * (max - min))
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(normalizedValue)
                .align(Alignment.CenterStart)
                .background(Color.White, RoundedCornerShape(18.dp))
        )
    }
}

@Composable
fun Vectorscope(histogram: IntArray?) {
    Box(modifier = Modifier.size(150.dp).clip(CircleShape).background(Color(0xAA000000)).padding(8.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.width / 2f
            
            // Draw crosshairs
            drawLine(Color.DarkGray, androidx.compose.ui.geometry.Offset(cx, 0f), androidx.compose.ui.geometry.Offset(cx, size.height))
            drawLine(Color.DarkGray, androidx.compose.ui.geometry.Offset(0f, cy), androidx.compose.ui.geometry.Offset(size.width, cy))
            
            // Draw targets (R, G, B, C, M, Y)
            drawCircle(Color.Red, 3f, androidx.compose.ui.geometry.Offset(cx + r * 0.8f * kotlin.math.cos(-Math.PI/6).toFloat(), cy + r * 0.8f * kotlin.math.sin(-Math.PI/6).toFloat()))
            drawCircle(Color.Green, 3f, androidx.compose.ui.geometry.Offset(cx + r * 0.8f * kotlin.math.cos(Math.PI/2).toFloat(), cy + r * 0.8f * kotlin.math.sin(Math.PI/2).toFloat()))
            drawCircle(Color.Blue, 3f, androidx.compose.ui.geometry.Offset(cx + r * 0.8f * kotlin.math.cos(Math.PI*7/6).toFloat(), cy + r * 0.8f * kotlin.math.sin(Math.PI*7/6).toFloat()))
            
            if (histogram != null) {
                // Simplistic vectorscope rendering from histogram data (not true pixel-by-pixel, but gives a rough idea of color distribution)
                for (i in 0..255) {
                    val countR = histogram[i]
                    val countG = histogram[256 + i]
                    val countB = histogram[512 + i]
                    
                    if (countR > 0 || countG > 0 || countB > 0) {
                        // Calculate hue and saturation roughly
                        val maxC = maxOf(countR, countG, countB).toFloat()
                        val minC = minOf(countR, countG, countB).toFloat()
                        val delta = maxC - minC
                        
                        var h = 0f
                        if (delta > 0) {
                            if (maxC == countR.toFloat()) h = 60f * (((countG - countB) / delta) % 6f)
                            else if (maxC == countG.toFloat()) h = 60f * (((countB - countR) / delta) + 2f)
                            else h = 60f * (((countR - countG) / delta) + 4f)
                        }
                        if (h < 0) h += 360f
                        
                        val s = if (maxC > 0) delta / maxC else 0f
                        
                        val angle = Math.toRadians(h.toDouble())
                        val dist = s * r * 0.8f
                        val px = cx + dist * kotlin.math.cos(angle).toFloat()
                        val py = cy - dist * kotlin.math.sin(angle).toFloat()
                        
                        drawCircle(Color.White.copy(alpha = 0.3f), 1.5f, androidx.compose.ui.geometry.Offset(px, py))
                    }
                }
            }
        }
    }
}

@Composable
fun WaveformScope(histogram: IntArray?) {
    Box(modifier = Modifier.size(150.dp, 100.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xAA000000)).padding(8.dp)) {
        if (histogram != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxVal = histogram.maxOrNull()?.toFloat() ?: 1f
                val w = size.width / 256f
                val h = size.height
                
                for (i in 0..255) {
                    val x = i * w
                    val lumaCount = (histogram[i] * 0.2126f + histogram[256 + i] * 0.7152f + histogram[512 + i] * 0.0722f)
                    val y = h - (lumaCount / maxVal) * h
                    drawLine(Color.White.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(x, h), androidx.compose.ui.geometry.Offset(x, y), strokeWidth = w)
                }
            }
        }
    }
}

@Composable
fun HistogramScope(histogram: IntArray?) {
    Box(modifier = Modifier.size(150.dp, 100.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xAA000000)).padding(8.dp)) {
        if (histogram != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxVal = histogram.maxOrNull()?.toFloat() ?: 1f
                val w = size.width / 256f
                val h = size.height
                
                val pathR = Path()
                val pathG = Path()
                val pathB = Path()
                
                pathR.moveTo(0f, h); pathG.moveTo(0f, h); pathB.moveTo(0f, h)
                
                for (i in 0..255) {
                    val x = i * w
                    pathR.lineTo(x, h - (histogram[i] / maxVal) * h)
                    pathG.lineTo(x, h - (histogram[256 + i] / maxVal) * h)
                    pathB.lineTo(x, h - (histogram[512 + i] / maxVal) * h)
                }
                
                pathR.lineTo(size.width, h); pathG.lineTo(size.width, h); pathB.lineTo(size.width, h)
                
                drawPath(pathR, Color.Red.copy(alpha = 0.5f), blendMode = BlendMode.Screen)
                drawPath(pathG, Color.Green.copy(alpha = 0.5f), blendMode = BlendMode.Screen)
                drawPath(pathB, Color.Blue.copy(alpha = 0.5f), blendMode = BlendMode.Screen)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val images = remember { mutableStateListOf<ImageItem>() }
    val overlays = remember { mutableStateListOf<OverlayItem>() }
    var maxZIndex by remember { mutableStateOf(0) }
    
    var engineMode by remember { mutableStateOf(EngineMode.LAB) }
    
    val cubes = remember { mutableStateListOf<CubeItem>() }
    val selectiveColors = remember { mutableStateListOf<SelectiveColor>() }
    
    // Auto Extracted Palette
    val autoPalette = remember { mutableStateListOf<Int>() }
    
    var adjustments by remember { mutableStateOf(Adjustments()) }
    var toning by remember { mutableStateOf(TonalAdjustments()) }
    var curves by remember { mutableStateOf(CurveSliders()) }
    var hslAdjustments by remember { mutableStateOf(com.agon.app.data.HslAdjustments()) }
    var colorWheels by remember { mutableStateOf(com.agon.app.data.ColorWheels()) }
    var advancedCurves by remember { mutableStateOf(com.agon.app.data.AdvancedCurves()) }
    var colorBalance by remember { mutableStateOf(com.agon.app.data.ColorBalance()) }
    var channelMixer by remember { mutableStateOf(com.agon.app.data.ChannelMixer()) }
    var cinematicEffects by remember { mutableStateOf(com.agon.app.data.CinematicEffects()) }
    
    // Match Color State
    var matchSourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var matchSourceStats by remember { mutableStateOf<FloatArray?>(null) }
    var matchTargetStats by remember { mutableStateOf<FloatArray?>(null) }
    var matchLum by remember { mutableStateOf(1f) }
    var matchIntensity by remember { mutableStateOf(1f) }
    var matchFade by remember { mutableStateOf(0f) }
    
    val history = remember { mutableStateListOf<EditorState>() }
    var historyIndex by remember { mutableStateOf(-1) }
    
    var isSaving by remember { mutableStateOf(false) }
    var isProcessingHighRes by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showScopes by remember { mutableStateOf(false) }
    var showHistogram by remember { mutableStateOf(false) }
    var showVectorscope by remember { mutableStateOf(false) }
    var showWaveform by remember { mutableStateOf(false) }
    var histogramData by remember { mutableStateOf<IntArray?>(null) }
    
    // Draggable Scopes State
    var scopesOffset by remember { mutableStateOf(Offset(50f, 50f)) }
    var scopesScale by remember { mutableStateOf(1f) }
    
    // Eyedropper states
    var pickingTargetFor by remember { mutableStateOf<String?>(null) }
    
    var showBeforeAfter by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0:CUBEs, 1:Select, 2:Match, 3:Toning, 4:Tones, 5:Adjust, 6:HSL, 7:Wheels, 8:Curves, 9:Balance, 10:Split, 11:Mixer, 12:Cinematic, 13:Overlays

    val updateChannel = remember { Channel<Unit>(Channel.CONFLATED) }

    fun saveHistory() {
        val state = EditorState(
            cubes = cubes.map { it.copy() },
            selectiveColors = selectiveColors.map { it.copy() },
            adjustments = adjustments.copy(),
            engineMode = engineMode,
            matchSourceStats = matchSourceStats?.clone(),
            matchTargetStats = matchTargetStats?.clone(),
            matchLum = matchLum,
            matchIntensity = matchIntensity,
            matchFade = matchFade,
            toning = toning.copy(),
            curves = curves.copy(),
            hslAdjustments = hslAdjustments.copy(),
            colorWheels = colorWheels.copy(),
            advancedCurves = advancedCurves.copy(),
            colorBalance = colorBalance.copy(),
            channelMixer = channelMixer.copy(),
            cinematicEffects = cinematicEffects.copy()
        )
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(state)
        historyIndex = history.size - 1
    }

    LaunchedEffect(Unit) {
        saveHistory()
        for (event in updateChannel) {
            if (images.isEmpty()) continue
            isProcessingHighRes = true
            
            if (matchSourceStats != null && matchTargetStats == null) {
                val topImg = images.maxByOrNull { it.zIndex }
                if (topImg != null) {
                    matchTargetStats = LutUtils.calculateLabStats(topImg.previewBitmap)
                }
            }
            
            val newLut = LutUtils.mixLutsAndAdjustments(
                cubes.map{Pair(it.lut, it.intensity)}, 
                selectiveColors.toList(), 
                adjustments, 
                engineMode,
                matchSourceStats, matchTargetStats, matchLum, matchIntensity, matchFade,
                toning, curves,
                hslAdjustments, colorWheels, advancedCurves, colorBalance, channelMixer, cinematicEffects,
                32
            )
            
            val updatedImages = images.map { img ->
                val newBmp = LutUtils.applyLutToBitmapFast(img.previewBitmap, newLut, img.pixelsBuffer, adjustments)
                img.copy(processedBitmap = newBmp)
            }
            
            if (showScopes) {
                val topImg = updatedImages.maxByOrNull { it.zIndex }
                if (topImg != null) {
                    histogramData = LutUtils.generateHistogram(topImg.processedBitmap ?: topImg.previewBitmap)
                }
            }
            
            withContext(Dispatchers.Main) {
                images.clear()
                images.addAll(updatedImages)
                isProcessingHighRes = false
            }
        }
    }

    val triggerUpdate = { updateChannel.trySend(Unit) }

    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val isGif = context.contentResolver.getType(uri)?.contains("gif") == true
            overlays.add(OverlayItem(uri = uri, isGif = isGif, zIndex = ++maxZIndex))
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) { isProcessingHighRes = true }
                for (uri in uris) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                        if (bitmap != null) {
                            val maxDim = 2048f 
                            val scale = minOf(maxDim / bitmap.width, maxDim / bitmap.height, 1f)
                            val preview = if (scale < 1f) {
                                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                            } else {
                                bitmap
                            }
                            val buffer = IntArray(preview.width * preview.height)
                            maxZIndex++
                            val newItem = ImageItem(uri = uri, originalBitmap = bitmap, previewBitmap = preview, pixelsBuffer = buffer, zIndex = maxZIndex)
                            withContext(Dispatchers.Main) { images.add(newItem) }
                            
                            // Auto Extract Palette
                            val extracted = LutUtils.extractPalette(preview, 6)
                            withContext(Dispatchers.Main) {
                                autoPalette.clear()
                                autoPalette.addAll(extracted)
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                triggerUpdate()
            }
        }
    }

    val cubeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) { isProcessingHighRes = true }
                for (uri in uris) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            val lut = LutUtils.parseCube(inputStream)
                            inputStream.close()
                            if (lut != null) {
                                withContext(Dispatchers.Main) { 
                                    cubes.add(CubeItem(name = "CUBE", lut = lut)) 
                                    saveHistory()
                                }
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                triggerUpdate()
            }
        }
    }

    val matchColorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (bitmap != null) {
                        // 1. Calculate global stats for Match Color sliders
                        val stats = LutUtils.calculateLabStats(bitmap)
                        
                        // 2. Extract Palette from Source to create explicit mappings
                        val sourcePalette = LutUtils.extractPalette(bitmap, 6)
                        
                        withContext(Dispatchers.Main) {
                            matchSourceBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                            matchSourceStats = stats
                            matchTargetStats = null
                            
                            // Map current autoPalette to sourcePalette
                            if (autoPalette.isNotEmpty() && sourcePalette.isNotEmpty()) {
                                selectiveColors.clear()
                                val minSize = minOf(autoPalette.size, sourcePalette.size)
                                for (i in 0 until minSize) {
                                    val srcCol = autoPalette[i]
                                    val tgtCol = sourcePalette[i]
                                    selectiveColors.add(SelectiveColor(UUID.randomUUID().toString(), srcCol, tgtCol))
                                }
                            }
                            
                            saveHistory()
                            triggerUpdate()
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    val paletteTargetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && pickingTargetFor != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (bitmap != null) {
                        val extracted = LutUtils.extractPalette(bitmap, 1)
                        if (extracted.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                val idx = selectiveColors.indexOfFirst { it.id == pickingTargetFor }
                                if (idx != -1) {
                                    selectiveColors[idx] = selectiveColors[idx].copy(targetColor = extracted[0])
                                }
                                pickingTargetFor = null
                                saveHistory()
                                triggerUpdate()
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        
        // Freeform Canvas Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 380.dp)
                .clipToBounds()
        ) {
            if (images.isEmpty()) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color(0xFF333333),
                    modifier = Modifier.align(Alignment.Center).size(120.dp)
                )
            } else {
                images.forEach { item ->
                    key(item.id) {
                        Image(
                            bitmap = (if(showBeforeAfter) item.previewBitmap else (item.processedBitmap ?: item.previewBitmap)).asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .zIndex(item.zIndex.toFloat())
                                .offset { IntOffset(item.offset.x.toInt(), item.offset.y.toInt()) }
                                .graphicsLayer(scaleX = item.scale, scaleY = item.scale)
                                .pointerInput(item.id) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val idx = images.indexOfFirst { it.id == item.id }
                                        if (idx != -1) {
                                            maxZIndex++
                                            images[idx] = images[idx].copy(
                                                offset = images[idx].offset + pan,
                                                scale = (images[idx].scale * zoom).coerceIn(0.1f, 10f),
                                                zIndex = maxZIndex
                                            )
                                        }
                                    }
                                }
                        )
                    }
                }
                
                overlays.forEach { overlay ->
                    key(overlay.id) {
                        coil3.compose.AsyncImage(
                            model = overlay.uri,
                            contentDescription = "Overlay",
                            modifier = Modifier
                                .zIndex(overlay.zIndex.toFloat())
                                .offset { IntOffset(overlay.offset.x.toInt(), overlay.offset.y.toInt()) }
                                .graphicsLayer(scaleX = overlay.scale, scaleY = overlay.scale)
                                .pointerInput(overlay.id) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val idx = overlays.indexOfFirst { it.id == overlay.id }
                                        if (idx != -1) {
                                            maxZIndex++
                                            overlays[idx] = overlays[idx].copy(
                                                offset = overlays[idx].offset + pan,
                                                scale = (overlays[idx].scale * zoom).coerceIn(0.1f, 10f),
                                                zIndex = maxZIndex
                                            )
                                        }
                                    }
                                }
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0x88000000))) {
                    IconButton(onClick = {
                        if (historyIndex > 0) {
                            historyIndex--
                            val state = history[historyIndex]
                            cubes.clear(); cubes.addAll(state.cubes.map{it.copy()})
                            selectiveColors.clear(); selectiveColors.addAll(state.selectiveColors.map{it.copy()})
                            adjustments = state.adjustments.copy()
                            engineMode = state.engineMode
                            matchSourceStats = state.matchSourceStats?.clone()
                            matchTargetStats = state.matchTargetStats?.clone()
                            matchLum = state.matchLum; matchIntensity = state.matchIntensity; matchFade = state.matchFade
                            toning = state.toning.copy(); curves = state.curves.copy()
                            hslAdjustments = state.hslAdjustments.copy(); colorWheels = state.colorWheels.copy()
                            advancedCurves = state.advancedCurves.copy(); colorBalance = state.colorBalance.copy()
                            channelMixer = state.channelMixer.copy(); cinematicEffects = state.cinematicEffects.copy()
                            triggerUpdate()
                        }
                    }) { Icon(Icons.Default.Undo, "Undo", tint = if(historyIndex>0) Color.White else Color.DarkGray) }
                    
                    IconButton(onClick = {
                        if (historyIndex < history.size - 1) {
                            historyIndex++
                            val state = history[historyIndex]
                            cubes.clear(); cubes.addAll(state.cubes.map{it.copy()})
                            selectiveColors.clear(); selectiveColors.addAll(state.selectiveColors.map{it.copy()})
                            adjustments = state.adjustments.copy()
                            engineMode = state.engineMode
                            matchSourceStats = state.matchSourceStats?.clone()
                            matchTargetStats = state.matchTargetStats?.clone()
                            matchLum = state.matchLum; matchIntensity = state.matchIntensity; matchFade = state.matchFade
                            toning = state.toning.copy(); curves = state.curves.copy()
                            hslAdjustments = state.hslAdjustments.copy(); colorWheels = state.colorWheels.copy()
                            advancedCurves = state.advancedCurves.copy(); colorBalance = state.colorBalance.copy()
                            channelMixer = state.channelMixer.copy(); cinematicEffects = state.cinematicEffects.copy()
                            triggerUpdate()
                        }
                    }) { Icon(Icons.Default.Redo, "Redo", tint = if(historyIndex<history.size-1) Color.White else Color.DarkGray) }
                }

                Row(modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0x88000000))) {
                    Box(modifier = Modifier.clickable { engineMode = EngineMode.RGB; saveHistory(); triggerUpdate() }.background(if(engineMode == EngineMode.RGB) Color(0xFF444444) else Color.Transparent, RoundedCornerShape(topStart=24.dp, bottomStart=24.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("RGB", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.clickable { engineMode = EngineMode.LAB; saveHistory(); triggerUpdate() }.background(if(engineMode == EngineMode.LAB) Color(0xFF444444) else Color.Transparent, RoundedCornerShape(topEnd=24.dp, bottomEnd=24.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("LAB", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.clip(CircleShape).background(if(showScopes) Color(0xFF444444) else Color(0x88000000)).clickable { showScopes = !showScopes; if(showScopes) triggerUpdate() }.padding(12.dp)
                    ) { Icon(Icons.Default.BarChart, "Scopes", tint = Color.White) }
                    
                    if (showScopes) {
                        Box(
                            modifier = Modifier.clip(CircleShape).background(if(showVectorscope) Color(0xFF444444) else Color(0x88000000)).clickable { showVectorscope = !showVectorscope }.padding(12.dp)
                        ) { Icon(Icons.Default.Radar, "Vectorscope", tint = Color.White) }
                        
                        Box(
                            modifier = Modifier.clip(CircleShape).background(if(showWaveform) Color(0xFF444444) else Color(0x88000000)).clickable { showWaveform = !showWaveform }.padding(12.dp)
                        ) { Icon(Icons.Default.Waves, "Waveform", tint = Color.White) }
                    }
                    
                    Box(
                        modifier = Modifier.clip(CircleShape).background(if(showBeforeAfter) Color(0xFF444444) else Color(0x88000000))
                            .pointerInput(Unit) { detectTapGestures(onPress = { showBeforeAfter = true; tryAwaitRelease(); showBeforeAfter = false }) }.padding(12.dp)
                    ) { Icon(Icons.Default.Compare, "Compare", tint = Color.White) }
                }
            }

            if (showScopes) {
                Box(
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(scopesOffset.x.toInt(), scopesOffset.y.toInt()) }
                        .graphicsLayer(scaleX = scopesScale, scaleY = scopesScale)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scopesScale = (scopesScale * zoom).coerceIn(0.5f, 3f)
                                scopesOffset += pan
                            }
                        }
                        .zIndex(9999f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HistogramScope(histogramData)
                        if (showVectorscope) Vectorscope(histogramData)
                        if (showWaveform) WaveformScope(histogramData)
                    }
                }
            }

            if (isProcessingHighRes) {
                Box(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xAA000000)).padding(horizontal = 16.dp, vertical = 8.dp).zIndex(9999f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Processing High-Res...", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Modern Glassmorphism Bottom Panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(380.dp)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color(0xEE1C1C1E))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
        ) {
            Box(
                modifier = Modifier.padding(top = 12.dp).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)).align(Alignment.CenterHorizontally)
            )
            
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                edgePadding = 16.dp,
                indicator = {},
                divider = {},
                modifier = Modifier.height(60.dp).padding(vertical = 8.dp)
            ) {
                TabButton(icon = Icons.Default.ViewInAr, title = "CUBEs", selected = selectedTab == 0) { selectedTab = 0 }
                TabButton(icon = Icons.Default.Colorize, title = "Selective", selected = selectedTab == 1) { selectedTab = 1 }
                TabButton(icon = Icons.Default.AutoFixHigh, title = "Match", selected = selectedTab == 2) { selectedTab = 2 }
                TabButton(icon = Icons.Default.Palette, title = "Toning", selected = selectedTab == 3) { selectedTab = 3 }
                TabButton(icon = Icons.Default.ShowChart, title = "Tones", selected = selectedTab == 4) { selectedTab = 4 }
                TabButton(icon = Icons.Default.Tune, title = "Adjust", selected = selectedTab == 5) { selectedTab = 5 }
                TabButton(icon = Icons.Default.ColorLens, title = "HSL", selected = selectedTab == 6) { selectedTab = 6 }
                TabButton(icon = Icons.Default.DonutLarge, title = "Wheels", selected = selectedTab == 7) { selectedTab = 7 }
                TabButton(icon = Icons.Default.Timeline, title = "Curves", selected = selectedTab == 8) { selectedTab = 8 }
                TabButton(icon = Icons.Default.Balance, title = "Balance", selected = selectedTab == 9) { selectedTab = 9 }
                TabButton(icon = Icons.Default.Splitscreen, title = "Split", selected = selectedTab == 10) { selectedTab = 10 }
                TabButton(icon = Icons.Default.Transform, title = "Mixer", selected = selectedTab == 11) { selectedTab = 11 }
                TabButton(icon = Icons.Default.MovieFilter, title = "Cinematic", selected = selectedTab == 12) { selectedTab = 12 }
                TabButton(icon = Icons.Default.Layers, title = "Overlays", selected = selectedTab == 13) { selectedTab = 13 }
            }
            
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(targetState = selectedTab, label = "TabContent") { tab ->
                    when (tab) {
                        0 -> {
                            LazyRow(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(32.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                itemsIndexed(cubes) { index, cube ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                                        IOSVerticalSlider(
                                            value = cube.intensity,
                                            onValueChange = { newInt ->
                                                cubes[index] = cubes[index].copy(intensity = newInt)
                                                triggerUpdate()
                                            },
                                            modifier = Modifier.height(200.dp).width(60.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        IconButton(onClick = { 
                                            cubes.removeAt(index)
                                            saveHistory()
                                            triggerUpdate()
                                        }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFFF5555))
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Auto Palette Display
                                if (autoPalette.isNotEmpty()) {
                                    Text("Image Palette (Tap to edit)", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(start = 24.dp, top = 8.dp))
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(autoPalette) { colorInt ->
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(colorInt))
                                                    .border(2.dp, Color.White, CircleShape)
                                                    .clickable {
                                                        selectiveColors.add(SelectiveColor(UUID.randomUUID().toString(), colorInt, colorInt))
                                                        saveHistory()
                                                        triggerUpdate()
                                                    }
                                            )
                                        }
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.DarkGray)
                                                    .border(2.dp, Color.White, CircleShape)
                                                    .clickable {
                                                        val defaultColor = android.graphics.Color.RED
                                                        selectiveColors.add(SelectiveColor(UUID.randomUUID().toString(), defaultColor, defaultColor))
                                                        saveHistory()
                                                        triggerUpdate()
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Add Custom Color", tint = Color.White, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                } else {
                                    // If no auto palette, still show the add button
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 24.dp, top = 16.dp)
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.DarkGray)
                                            .border(2.dp, Color.White, CircleShape)
                                            .clickable {
                                                val defaultColor = android.graphics.Color.RED
                                                selectiveColors.add(SelectiveColor(UUID.randomUUID().toString(), defaultColor, defaultColor))
                                                saveHistory()
                                                triggerUpdate()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Custom Color", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                                
                                // Active Selective Colors
                                LazyRow(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    itemsIndexed(selectiveColors) { index, sc ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(140.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Box(
                                                    modifier = Modifier.size(16.dp).clip(CircleShape).background(if(sc.isEnabled) Color.Green else Color.DarkGray).clickable { 
                                                        selectiveColors[index] = selectiveColors[index].copy(isEnabled = !sc.isEnabled)
                                                        triggerUpdate()
                                                    }
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(sc.targetColor))
                                                        .border(2.dp, Color(sc.sourceColor), CircleShape)
                                                        .clickable { 
                                                            pickingTargetFor = sc.id 
                                                            paletteTargetLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                                        }
                                                ) {
                                                    // Pick source color directly
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.Center)
                                                            .size(12.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.White.copy(alpha = 0.5f))
                                                            .clickable {
                                                                // Future: Add a color picker dialog here.
                                                                // For now, cycle through primary colors
                                                                val nextColor = when (sc.sourceColor) {
                                                                    android.graphics.Color.RED -> android.graphics.Color.GREEN
                                                                    android.graphics.Color.GREEN -> android.graphics.Color.BLUE
                                                                    android.graphics.Color.BLUE -> android.graphics.Color.YELLOW
                                                                    android.graphics.Color.YELLOW -> android.graphics.Color.CYAN
                                                                    android.graphics.Color.CYAN -> android.graphics.Color.MAGENTA
                                                                    else -> android.graphics.Color.RED
                                                                }
                                                                selectiveColors[index] = selectiveColors[index].copy(sourceColor = nextColor, targetColor = nextColor)
                                                                triggerUpdate()
                                                            }
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            Row(modifier = Modifier.height(110.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    IOSVerticalSlider(value = sc.intensity, onValueChange = { selectiveColors[index] = selectiveColors[index].copy(intensity = it); triggerUpdate() }, modifier = Modifier.weight(1f).width(24.dp), activeColor = Color.White)
                                                    Text("Int", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top=4.dp))
                                                }
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    IOSVerticalSlider(value = sc.saturation / 2f, onValueChange = { selectiveColors[index] = selectiveColors[index].copy(saturation = it * 2f); triggerUpdate() }, modifier = Modifier.weight(1f).width(24.dp), activeColor = Color.Cyan)
                                                    Text("Sat", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top=4.dp))
                                                }
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    IOSVerticalSlider(value = (sc.lightness + 1f) / 2f, onValueChange = { selectiveColors[index] = selectiveColors[index].copy(lightness = it * 2f - 1f); triggerUpdate() }, modifier = Modifier.weight(1f).width(24.dp), activeColor = Color.Yellow)
                                                    Text("Lum", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top=4.dp))
                                                }
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    IOSVerticalSlider(value = sc.range / 180f, onValueChange = { selectiveColors[index] = selectiveColors[index].copy(range = it * 180f); triggerUpdate() }, modifier = Modifier.weight(1f).width(24.dp), activeColor = Color.Magenta)
                                                    Text("Rng", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top=4.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            IconButton(onClick = { 
                                                selectiveColors.removeAt(index)
                                                saveHistory()
                                                triggerUpdate()
                                            }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFFF5555))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Button(
                                    onClick = { matchColorLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                                ) {
                                    Icon(Icons.Default.ImageSearch, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Load Source Image / Palette", color = Color.White)
                                }
                                
                                if (matchSourceBitmap != null) {
                                    Image(
                                        bitmap = matchSourceBitmap!!.asImageBitmap(),
                                        contentDescription = "Source",
                                        modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    
                                    AdjustmentSlider("Luminance", matchLum, 0f, 2f) { matchLum = it; triggerUpdate() }
                                    AdjustmentSlider("Color Intensity", matchIntensity, 0f, 2f) { matchIntensity = it; triggerUpdate() }
                                    AdjustmentSlider("Fade", matchFade, 0f, 1f) { matchFade = it; triggerUpdate() }
                                    
                                    Text("Note: Match Color automatically created color pairs in the Selective tab!", color = Color.Green, fontSize = 10.sp)
                                }
                            }
                        }
                        3 -> {
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("Shadows", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Hue", toning.shadowHue, 0f, 360f) { toning = toning.copy(shadowHue = it); triggerUpdate() }
                                AdjustmentSlider("Saturation", toning.shadowSat, 0f, 1f) { toning = toning.copy(shadowSat = it); triggerUpdate() }
                                AdjustmentSlider("Lightness", toning.shadowLum, -1f, 1f) { toning = toning.copy(shadowLum = it); triggerUpdate() }
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text("Midtones", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Hue", toning.midHue, 0f, 360f) { toning = toning.copy(midHue = it); triggerUpdate() }
                                AdjustmentSlider("Saturation", toning.midSat, 0f, 1f) { toning = toning.copy(midSat = it); triggerUpdate() }
                                AdjustmentSlider("Lightness", toning.midLum, -1f, 1f) { toning = toning.copy(midLum = it); triggerUpdate() }
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text("Highlights", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Hue", toning.highHue, 0f, 360f) { toning = toning.copy(highHue = it); triggerUpdate() }
                                AdjustmentSlider("Saturation", toning.highSat, 0f, 1f) { toning = toning.copy(highSat = it); triggerUpdate() }
                                AdjustmentSlider("Lightness", toning.highLum, -1f, 1f) { toning = toning.copy(highLum = it); triggerUpdate() }
                            }
                        }
                        4 -> {
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                AdjustmentSlider("Blacks", curves.blacks, 0f, 0.5f) { curves = curves.copy(blacks = it); triggerUpdate() }
                                AdjustmentSlider("Shadows", curves.shadows, 0f, 0.5f) { curves = curves.copy(shadows = it); triggerUpdate() }
                                AdjustmentSlider("Midtones", curves.midtones, 0.25f, 0.75f) { curves = curves.copy(midtones = it); triggerUpdate() }
                                AdjustmentSlider("Highlights", curves.highlights, 0.5f, 1f) { curves = curves.copy(highlights = it); triggerUpdate() }
                                AdjustmentSlider("Whites", curves.whites, 0.5f, 1f) { curves = curves.copy(whites = it); triggerUpdate() }
                            }
                        }
                        5 -> {
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                AdjustmentSlider("Exposure", adjustments.exposure, -2f, 2f) { adjustments = adjustments.copy(exposure = it); triggerUpdate() }
                                AdjustmentSlider("Highlights Rec", adjustments.highlightsRecovery, 0f, 1f) { adjustments = adjustments.copy(highlightsRecovery = it); triggerUpdate() }
                                AdjustmentSlider("Shadows Rec", adjustments.shadowsRecovery, 0f, 1f) { adjustments = adjustments.copy(shadowsRecovery = it); triggerUpdate() }
                                AdjustmentSlider("Whites", adjustments.whites, -1f, 1f) { adjustments = adjustments.copy(whites = it); triggerUpdate() }
                                AdjustmentSlider("Blacks", adjustments.blacks, -1f, 1f) { adjustments = adjustments.copy(blacks = it); triggerUpdate() }
                                AdjustmentSlider("Temp", adjustments.temp, -1f, 1f) { adjustments = adjustments.copy(temp = it); triggerUpdate() }
                                AdjustmentSlider("Tint", adjustments.tint, -1f, 1f) { adjustments = adjustments.copy(tint = it); triggerUpdate() }
                                AdjustmentSlider("Saturation", adjustments.saturation, 0f, 2f) { adjustments = adjustments.copy(saturation = it); triggerUpdate() }
                                AdjustmentSlider("Vibrance", adjustments.vibrance, -1f, 1f) { adjustments = adjustments.copy(vibrance = it); triggerUpdate() }
                                AdjustmentSlider("Clarity", adjustments.clarity, -1f, 1f) { adjustments = adjustments.copy(clarity = it); triggerUpdate() }
                                AdjustmentSlider("Dehaze", adjustments.dehaze, -1f, 1f) { adjustments = adjustments.copy(dehaze = it); triggerUpdate() }
                                AdjustmentSlider("Contrast", adjustments.contrast, 0.5f, 1.5f) { adjustments = adjustments.copy(contrast = it); triggerUpdate() }
                                AdjustmentSlider("Ultra Black", adjustments.ultraBlack, -0.5f, 0.5f) { adjustments = adjustments.copy(ultraBlack = it); triggerUpdate() }
                                AdjustmentSlider("Ultra White", adjustments.ultraWhite, 0.5f, 1.5f) { adjustments = adjustments.copy(ultraWhite = it); triggerUpdate() }
                                AdjustmentSlider("Texture", adjustments.texture, -1f, 1f) { adjustments = adjustments.copy(texture = it); triggerUpdate() }
                                AdjustmentSlider("Film Grain", adjustments.grain, 0f, 1f) { adjustments = adjustments.copy(grain = it); triggerUpdate() }
                            }
                        }
                        6 -> {
                            // HSL Panel
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                HslSliders("Red", hslAdjustments.red) { hslAdjustments = hslAdjustments.copy(red = it); triggerUpdate() }
                                HslSliders("Orange", hslAdjustments.orange) { hslAdjustments = hslAdjustments.copy(orange = it); triggerUpdate() }
                                HslSliders("Yellow", hslAdjustments.yellow) { hslAdjustments = hslAdjustments.copy(yellow = it); triggerUpdate() }
                                HslSliders("Green", hslAdjustments.green) { hslAdjustments = hslAdjustments.copy(green = it); triggerUpdate() }
                                HslSliders("Cyan", hslAdjustments.cyan) { hslAdjustments = hslAdjustments.copy(cyan = it); triggerUpdate() }
                                HslSliders("Blue", hslAdjustments.blue) { hslAdjustments = hslAdjustments.copy(blue = it); triggerUpdate() }
                                HslSliders("Purple", hslAdjustments.purple) { hslAdjustments = hslAdjustments.copy(purple = it); triggerUpdate() }
                                HslSliders("Magenta", hslAdjustments.magenta) { hslAdjustments = hslAdjustments.copy(magenta = it); triggerUpdate() }
                            }
                        }
                        7 -> {
                            // Color Wheels
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("Lift (Shadows)", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Hue Angle", colorWheels.liftAngle, 0f, 360f) { colorWheels = colorWheels.copy(liftAngle = it); triggerUpdate() }
                                AdjustmentSlider("Intensity", colorWheels.liftRadius, 0f, 1f) { colorWheels = colorWheels.copy(liftRadius = it); triggerUpdate() }
                                AdjustmentSlider("Luma", colorWheels.liftY, -1f, 1f) { colorWheels = colorWheels.copy(liftY = it); triggerUpdate() }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Gamma (Midtones)", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Hue Angle", colorWheels.gammaAngle, 0f, 360f) { colorWheels = colorWheels.copy(gammaAngle = it); triggerUpdate() }
                                AdjustmentSlider("Intensity", colorWheels.gammaRadius, 0f, 1f) { colorWheels = colorWheels.copy(gammaRadius = it); triggerUpdate() }
                                AdjustmentSlider("Luma", colorWheels.gammaY, -1f, 1f) { colorWheels = colorWheels.copy(gammaY = it); triggerUpdate() }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Gain (Highlights)", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Hue Angle", colorWheels.gainAngle, 0f, 360f) { colorWheels = colorWheels.copy(gainAngle = it); triggerUpdate() }
                                AdjustmentSlider("Intensity", colorWheels.gainRadius, 0f, 1f) { colorWheels = colorWheels.copy(gainRadius = it); triggerUpdate() }
                                AdjustmentSlider("Luma", colorWheels.gainY, -1f, 1f) { colorWheels = colorWheels.copy(gainY = it); triggerUpdate() }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Offset", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Hue Angle", colorWheels.offsetAngle, 0f, 360f) { colorWheels = colorWheels.copy(offsetAngle = it); triggerUpdate() }
                                AdjustmentSlider("Intensity", colorWheels.offsetRadius, 0f, 1f) { colorWheels = colorWheels.copy(offsetRadius = it); triggerUpdate() }
                                AdjustmentSlider("Luma", colorWheels.offsetY, -1f, 1f) { colorWheels = colorWheels.copy(offsetY = it); triggerUpdate() }
                            }
                        }
                        8 -> {
                            // Advanced Curves
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                AdvancedCurveEditor("RGB Curve", advancedCurves.rgb) { advancedCurves = advancedCurves.copy(rgb = it); triggerUpdate() }
                                AdvancedCurveEditor("Red Curve", advancedCurves.red) { advancedCurves = advancedCurves.copy(red = it); triggerUpdate() }
                                AdvancedCurveEditor("Green Curve", advancedCurves.green) { advancedCurves = advancedCurves.copy(green = it); triggerUpdate() }
                                AdvancedCurveEditor("Blue Curve", advancedCurves.blue) { advancedCurves = advancedCurves.copy(blue = it); triggerUpdate() }
                                AdvancedCurveEditor("Luminance Curve", advancedCurves.lum) { advancedCurves = advancedCurves.copy(lum = it); triggerUpdate() }
                            }
                        }
                        9 -> {
                            // Color Balance
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("Shadows", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Cyan - Red", colorBalance.shadowsCyanRed, -1f, 1f) { colorBalance = colorBalance.copy(shadowsCyanRed = it); triggerUpdate() }
                                AdjustmentSlider("Magenta - Green", colorBalance.shadowsMagentaGreen, -1f, 1f) { colorBalance = colorBalance.copy(shadowsMagentaGreen = it); triggerUpdate() }
                                AdjustmentSlider("Yellow - Blue", colorBalance.shadowsYellowBlue, -1f, 1f) { colorBalance = colorBalance.copy(shadowsYellowBlue = it); triggerUpdate() }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Midtones", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Cyan - Red", colorBalance.midtonesCyanRed, -1f, 1f) { colorBalance = colorBalance.copy(midtonesCyanRed = it); triggerUpdate() }
                                AdjustmentSlider("Magenta - Green", colorBalance.midtonesMagentaGreen, -1f, 1f) { colorBalance = colorBalance.copy(midtonesMagentaGreen = it); triggerUpdate() }
                                AdjustmentSlider("Yellow - Blue", colorBalance.midtonesYellowBlue, -1f, 1f) { colorBalance = colorBalance.copy(midtonesYellowBlue = it); triggerUpdate() }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Highlights", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Cyan - Red", colorBalance.highlightsCyanRed, -1f, 1f) { colorBalance = colorBalance.copy(highlightsCyanRed = it); triggerUpdate() }
                                AdjustmentSlider("Magenta - Green", colorBalance.highlightsMagentaGreen, -1f, 1f) { colorBalance = colorBalance.copy(highlightsMagentaGreen = it); triggerUpdate() }
                                AdjustmentSlider("Yellow - Blue", colorBalance.highlightsYellowBlue, -1f, 1f) { colorBalance = colorBalance.copy(highlightsYellowBlue = it); triggerUpdate() }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = colorBalance.preserveLuminance,
                                        onCheckedChange = { colorBalance = colorBalance.copy(preserveLuminance = it); triggerUpdate() },
                                        colors = CheckboxDefaults.colors(checkedColor = Color.White, checkmarkColor = Color.Black)
                                    )
                                    Text("Preserve Luminance", color = Color.White)
                                }
                            }
                        }
                        10 -> {
                            // Split Toning
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("Highlights", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Hue", toning.highHue, 0f, 360f) { toning = toning.copy(highHue = it); triggerUpdate() }
                                AdjustmentSlider("Saturation", toning.highSat, 0f, 1f) { toning = toning.copy(highSat = it); triggerUpdate() }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Shadows", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Hue", toning.shadowHue, 0f, 360f) { toning = toning.copy(shadowHue = it); triggerUpdate() }
                                AdjustmentSlider("Saturation", toning.shadowSat, 0f, 1f) { toning = toning.copy(shadowSat = it); triggerUpdate() }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                AdjustmentSlider("Balance", toning.balance, -1f, 1f) { toning = toning.copy(balance = it); triggerUpdate() }
                            }
                        }
                        11 -> {
                            // Channel Mixer
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("Red Channel", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Red in Red", channelMixer.redInRed, -2f, 2f) { channelMixer = channelMixer.copy(redInRed = it); triggerUpdate() }
                                AdjustmentSlider("Green in Red", channelMixer.greenInRed, -2f, 2f) { channelMixer = channelMixer.copy(greenInRed = it); triggerUpdate() }
                                AdjustmentSlider("Blue in Red", channelMixer.blueInRed, -2f, 2f) { channelMixer = channelMixer.copy(blueInRed = it); triggerUpdate() }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Green Channel", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Red in Green", channelMixer.redInGreen, -2f, 2f) { channelMixer = channelMixer.copy(redInGreen = it); triggerUpdate() }
                                AdjustmentSlider("Green in Green", channelMixer.greenInGreen, -2f, 2f) { channelMixer = channelMixer.copy(greenInGreen = it); triggerUpdate() }
                                AdjustmentSlider("Blue in Green", channelMixer.blueInGreen, -2f, 2f) { channelMixer = channelMixer.copy(blueInGreen = it); triggerUpdate() }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Blue Channel", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Red in Blue", channelMixer.redInBlue, -2f, 2f) { channelMixer = channelMixer.copy(redInBlue = it); triggerUpdate() }
                                AdjustmentSlider("Green in Blue", channelMixer.greenInBlue, -2f, 2f) { channelMixer = channelMixer.copy(greenInBlue = it); triggerUpdate() }
                                AdjustmentSlider("Blue in Blue", channelMixer.blueInBlue, -2f, 2f) { channelMixer = channelMixer.copy(blueInBlue = it); triggerUpdate() }
                            }
                        }
                        12 -> {
                            // Cinematic Effects
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = cinematicEffects.falseColorEnabled,
                                        onCheckedChange = { cinematicEffects = cinematicEffects.copy(falseColorEnabled = it); triggerUpdate() },
                                        colors = CheckboxDefaults.colors(checkedColor = Color.White, checkmarkColor = Color.Black)
                                    )
                                    Text("False Color (IRE Exposure Check)", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Film Halation", color = Color.White, fontWeight = FontWeight.Bold)
                                AdjustmentSlider("Intensity", cinematicEffects.halationIntensity, 0f, 1f) { cinematicEffects = cinematicEffects.copy(halationIntensity = it); triggerUpdate() }
                                AdjustmentSlider("Threshold", cinematicEffects.halationThreshold, 0.5f, 1f) { cinematicEffects = cinematicEffects.copy(halationThreshold = it); triggerUpdate() }
                            }
                        }
                        13 -> {
                            // Overlays
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Button(onClick = { overlayLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                    Text("Add Overlay / GIF")
                                }
                                Text("Select an overlay to adjust its position and scale on the canvas.", color = Color.Gray, fontSize = 12.sp)
                                
                                overlays.forEach { overlay ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text("Overlay \${overlay.id.take(4)}", color = Color.White, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { overlays.remove(overlay) }) {
                                            Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Load Images", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                
                IconButton(onClick = { 
                    if (selectedTab == 0) cubeLauncher.launch("*/*")
                }) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Add Item", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                
                IconButton(onClick = { if (!isSaving) showExportDialog = true }) {
                    if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    else Icon(Icons.Default.SaveAlt, contentDescription = "Export", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }

    if (showExportDialog) {
        var exportSize by remember { mutableStateOf(32) }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Quality & Format", color = Color.White) },
            containerColor = Color(0xFF222222),
            text = { 
                Column {
                    Text("Select CUBE Export Grid Size:", color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    val options = listOf(16 to "16 Bit (Fast)", 32 to "32 Bit (Standard)", 64 to "64 Bit (Pro)", 128 to "128 Bit (Ultra)")
                    options.forEach { (size, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { exportSize = size }.padding(vertical = 8.dp)) {
                            RadioButton(selected = exportSize == size, onClick = { exportSize = size }, colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color.Gray))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Note: Texture and Grain are only saved in PNG export.", color = Color.Gray, fontSize = 10.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    isSaving = true
                    coroutineScope.launch {
                        saveMixedCube(context, cubes.map { Pair(it.lut, it.intensity) }, selectiveColors.toList(), adjustments, engineMode, matchSourceStats, matchTargetStats, matchLum, matchIntensity, matchFade, toning, curves, exportSize)
                        isSaving = false
                    }
                }) { Text("Export .CUBE", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    if (images.isNotEmpty()) {
                        isSaving = true
                        coroutineScope.launch {
                            val finalLut = LutUtils.mixLutsAndAdjustments(cubes.map { Pair(it.lut, it.intensity) }, selectiveColors.toList(), adjustments, engineMode, matchSourceStats, matchTargetStats, matchLum, matchIntensity, matchFade, toning, curves, hslAdjustments, colorWheels, advancedCurves, colorBalance, channelMixer, cinematicEffects, 64)
                            images.forEach { img -> saveImage(context, img.originalBitmap, finalLut, adjustments) }
                            isSaving = false
                        }
                    } else { Toast.makeText(context, "No images to save", Toast.LENGTH_SHORT).show() }
                }) { Text("Export Full-Res PNG", color = Color.White) }
            }
        )
    }
}

@Composable
fun TabButton(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.padding(horizontal = 4.dp).fillMaxHeight().width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0x33FFFFFF) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(24.dp))
            Text(title, color = Color.White, fontSize = 10.sp)
        }
    }
}

@Composable
fun HslSliders(colorName: String, color: com.agon.app.data.HslColor, onColorChange: (com.agon.app.data.HslColor) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(colorName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        AdjustmentSlider("Hue", color.hue, -1f, 1f) { onColorChange(color.copy(hue = it)) }
        Spacer(modifier = Modifier.height(8.dp))
        AdjustmentSlider("Saturation", color.saturation, -1f, 1f) { onColorChange(color.copy(saturation = it)) }
        Spacer(modifier = Modifier.height(8.dp))
        AdjustmentSlider("Luminance", color.luminance, -1f, 1f) { onColorChange(color.copy(luminance = it)) }
    }
}

@Composable
fun AdvancedCurveEditor(title: String, points: FloatArray, onPointsChange: (FloatArray) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
        // Basic sliders for the 5 points (0, 0.25, 0.5, 0.75, 1.0)
        AdjustmentSlider("Point 1 (Blacks)", points[1], 0f, 1f) { val new = points.clone(); new[1] = it; onPointsChange(new) }
        AdjustmentSlider("Point 2 (Shadows)", points[3], 0f, 1f) { val new = points.clone(); new[3] = it; onPointsChange(new) }
        AdjustmentSlider("Point 3 (Midtones)", points[5], 0f, 1f) { val new = points.clone(); new[5] = it; onPointsChange(new) }
        AdjustmentSlider("Point 4 (Highlights)", points[7], 0f, 1f) { val new = points.clone(); new[7] = it; onPointsChange(new) }
        AdjustmentSlider("Point 5 (Whites)", points[9], 0f, 1f) { val new = points.clone(); new[9] = it; onPointsChange(new) }
    }
}

@Composable
fun AdjustmentSlider(label: String, value: Float, min: Float, max: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(String.format("%.2f", value), color = Color.Gray, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        IOSHorizontalSlider(value = value, min = min, max = max, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth())
    }
}

suspend fun saveImage(context: Context, originalBitmap: Bitmap, mixedLut: CubeLut?, adj: Adjustments) {
    withContext(Dispatchers.IO) {
        try {
            val resultBitmap = if (mixedLut != null) LutUtils.applyLutToBitmapFast(originalBitmap, mixedLut, IntArray(originalBitmap.width * originalBitmap.height), adj) else originalBitmap
            val filename = "ProEdit_\${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            val outputUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (outputUri != null) {
                context.contentResolver.openOutputStream(outputUri)?.use { out -> resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                withContext(Dispatchers.Main) { Toast.makeText(context, "Full-Res Image Saved", Toast.LENGTH_SHORT).show() }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

suspend fun saveMixedCube(
    context: Context, 
    luts: List<Pair<CubeLut, Float>>, 
    selective: List<SelectiveColor>, 
    adj: Adjustments, 
    engineMode: EngineMode, 
    matchSourceStats: FloatArray?,
    matchTargetStats: FloatArray?,
    matchLum: Float,
    matchIntensity: Float,
    matchFade: Float,
    toning: TonalAdjustments,
    curves: CurveSliders,
    size: Int
) {
    withContext(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) { Toast.makeText(context, "Generating \${size}-bit CUBE...", Toast.LENGTH_LONG).show() }
            val mixed = LutUtils.mixLutsAndAdjustments(luts, selective, adj, engineMode, matchSourceStats, matchTargetStats, matchLum, matchIntensity, matchFade, toning, curves, com.agon.app.data.HslAdjustments(), com.agon.app.data.ColorWheels(), com.agon.app.data.AdvancedCurves(), com.agon.app.data.ColorBalance(), com.agon.app.data.ChannelMixer(), com.agon.app.data.CinematicEffects(), size)
            val filename = "Mixed_\${engineMode.name}_\${size}bit_\${System.currentTimeMillis()}.cube"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val outputUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (outputUri != null) {
                context.contentResolver.openOutputStream(outputUri)?.use { out ->
                    val file = File(context.cacheDir, "temp.cube")
                    LutUtils.exportCube(mixed, file)
                    file.inputStream().use { input -> input.copyTo(out) }
                    file.delete()
                }
                withContext(Dispatchers.Main) { Toast.makeText(context, "Mixed CUBE Saved to Downloads", Toast.LENGTH_LONG).show() }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}