package org.primftpd.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import org.primftpd.ui.data.ChartPeak
import org.primftpd.ui.data.ChartTriStateEnum
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.Component
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.ranges.ClosedFloatingPointRange

private val AXIS_TIME_WITH_SECONDS_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
private val AXIS_TIME_WITH_MINUTES_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val AXIS_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.US)

private fun formatAxisTime(value: Double, xLengthSeconds: Double): String {
    val dateTime = java.time.LocalDateTime.ofInstant(
        Instant.ofEpochSecond(value.toLong()),
        ZoneId.systemDefault(),
    )
    val formatter = when {
        xLengthSeconds <= 60.0 -> AXIS_TIME_WITH_SECONDS_FORMATTER
        xLengthSeconds <= 24.0 * 60.0 * 60.0 -> AXIS_TIME_WITH_MINUTES_FORMATTER
        else -> AXIS_DATE_TIME_FORMATTER
    }
    return dateTime.format(formatter)
}

private fun formatAxisSpeed(kilobytesPerSecond: Double): String {
    val absValue = abs(kilobytesPerSecond)
    return when {
        absValue >= 1024.0 * 1024.0 ->
            String.format(Locale.US, "%.1f GB/s", kilobytesPerSecond / 1024.0 / 1024.0)
        absValue >= 1024.0 ->
            String.format(Locale.US, "%.1f MB/s", kilobytesPerSecond / 1024.0)
        else ->
            String.format(Locale.US, "%.0f KB/s", kilobytesPerSecond)
    }
}



private val noMarginBottomAxisItemPlacer = object :
    HorizontalAxis.ItemPlacer by HorizontalAxis.ItemPlacer.aligned() {
    override fun getStartLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = 0f

    override fun getEndLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = 0f

    // 不要为了让首尾刻度标签完整显示而给数据层加额外内边距
    override fun getFirstLabelValue(
        context: CartesianMeasuringContext,
        maxLabelWidth: Float,
    ): Double? = null

    override fun getLastLabelValue(
        context: CartesianMeasuringContext,
        maxLabelWidth: Float,
    ): Double? = null
}

private val noMarginBottomAxisItemPlacerForDay = object :
    HorizontalAxis.ItemPlacer by HorizontalAxis.ItemPlacer.aligned(spacing = { 4 * 30 * 60 }) {
    override fun getStartLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = 0f

    override fun getEndLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = 0f

    override fun getFirstLabelValue(
        context: CartesianMeasuringContext,
        maxLabelWidth: Float,
    ): Double? = null

    override fun getLastLabelValue(
        context: CartesianMeasuringContext,
        maxLabelWidth: Float,
    ): Double? = null
}
private val noMarginBottomAxisItemPlacerForHour = object :
    HorizontalAxis.ItemPlacer by HorizontalAxis.ItemPlacer.aligned(spacing = { 4 * 60 }) {
    override fun getStartLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = 0f

    override fun getEndLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = 0f

    override fun getFirstLabelValue(
        context: CartesianMeasuringContext,
        maxLabelWidth: Float,
    ): Double? = null

    override fun getLastLabelValue(
        context: CartesianMeasuringContext,
        maxLabelWidth: Float,
    ): Double? = null
}

private val noMarginBottomAxisItemPlacerForMinute = object :
    HorizontalAxis.ItemPlacer by HorizontalAxis.ItemPlacer.aligned(spacing = { 1 }) {
    override fun getStartLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = 0f

    override fun getEndLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = 0f

    override fun getFirstLabelValue(
        context: CartesianMeasuringContext,
        maxLabelWidth: Float,
    ): Double? = null

    override fun getLastLabelValue(
        context: CartesianMeasuringContext,
        maxLabelWidth: Float,
    ): Double? = null
}



private class PeakDotMarker(
    private val halo: Component,
    private val dot: Component,
    private val dotSize: Dp,
    private val haloSize: Dp,
    private val expectedY: Double,
) : CartesianMarker {
    override fun drawOverLayers(
        context: CartesianDrawingContext,
        targets: List<CartesianMarker.Target>,
    ) {
        val dotHalf = with(context.density) { dotSize.toPx() } / 2f
        val haloHalf = with(context.density) { haloSize.toPx() } / 2f
        targets.forEach { target ->
            val lineTarget = target as? LineCartesianLayerMarkerTarget ?: return@forEach
            val point = lineTarget.points.firstOrNull {
                abs(it.entry.y - expectedY) < 0.5
            } ?: return@forEach
            halo.draw(
                context,
                target.canvasX - haloHalf,
                point.canvasY - haloHalf,
                target.canvasX + haloHalf,
                point.canvasY + haloHalf,
            )
            dot.draw(
                context,
                target.canvasX - dotHalf,
                point.canvasY - dotHalf,
                target.canvasX + dotHalf,
                point.canvasY + dotHalf,
            )
        }
    }
}

@Composable
fun NetworkTrafficChart(
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier,
    peakPoints: List<ChartPeak> = emptyList(),
    measuringRule: ChartTriStateEnum = ChartTriStateEnum.HOUR,
) {
    val ftpLineColor = Color(0xFFB39DDB)
    val sftpLineColor = Color(0xFF81C784)

    val horizontalAxisValueFormatter = remember {
        CartesianValueFormatter { context, value, _ ->
            formatAxisTime(value, context.ranges.xLength)
        }
    }
    val verticalAxisValueFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            formatAxisSpeed(value)
        }
    }

    val ftpPeakPoint = peakPoints.firstOrNull { it.isFtp }
    val sftpPeakPoint = peakPoints.firstOrNull { !it.isFtp }

    val ftpPeakHalo = rememberShapeComponent(
        fill = Fill(ftpLineColor.copy(alpha = 0.18f)),
        shape = CircleShape,
    )
    val ftpPeakDot = rememberShapeComponent(
        fill = Fill(ftpLineColor),
        shape = CircleShape,
    )
    val sftpPeakHalo = rememberShapeComponent(
        fill = Fill(sftpLineColor.copy(alpha = 0.18f)),
        shape = CircleShape,
    )
    val sftpPeakDot = rememberShapeComponent(
        fill = Fill(sftpLineColor),
        shape = CircleShape,
    )
    val ftpPeakMarker = remember(ftpPeakHalo, ftpPeakDot, ftpPeakPoint?.valueKbPerSecond) {
        PeakDotMarker(
            halo = ftpPeakHalo,
            dot = ftpPeakDot,
            dotSize = 7.dp,
            haloSize = 18.dp,
            expectedY = ftpPeakPoint?.valueKbPerSecond?.toDouble() ?: Double.NaN,
        )
    }
    val sftpPeakMarker = remember(sftpPeakHalo, sftpPeakDot, sftpPeakPoint?.valueKbPerSecond) {
        PeakDotMarker(
            halo = sftpPeakHalo,
            dot = sftpPeakDot,
            dotSize = 7.dp,
            haloSize = 18.dp,
            expectedY = sftpPeakPoint?.valueKbPerSecond?.toDouble() ?: Double.NaN,
        )
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    // 第一根线 (FTP)
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(Fill(ftpLineColor)),
                        areaFill = LineCartesianLayer.AreaFill.single(
                            Fill(Brush.verticalGradient(listOf(ftpLineColor.copy(alpha = 0.4f), Color.Transparent)))
                        ),
                        interpolator = LineCartesianLayer.Interpolator.Sharp
                    ),
                    // 第二根线 (SFTP)
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(Fill(sftpLineColor)),
                        areaFill = LineCartesianLayer.AreaFill.single(
                            Fill(Brush.verticalGradient(listOf(sftpLineColor.copy(alpha = 0.4f), Color.Transparent)))
                        ),
                        interpolator = LineCartesianLayer.Interpolator.Sharp
                    ),
                )
            ),
            // 去除网格线；纵轴显示速度单位，横轴按时间戳显示本地时间。
            startAxis = VerticalAxis.rememberStart(
                guideline = null,
                valueFormatter = verticalAxisValueFormatter,
            ),

            bottomAxis = HorizontalAxis.rememberBottom(
                label = rememberAxisLabelComponent(
                        overflow = TextOverflow.Visible,
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (isSystemInDarkTheme()) Color.White else Color.Black),
                    ),
                guideline = null,
                itemPlacer = when (measuringRule) {
                    ChartTriStateEnum.DAY -> {
                        noMarginBottomAxisItemPlacerForDay
                    }
                    ChartTriStateEnum.HOUR -> {
                        noMarginBottomAxisItemPlacerForHour
                    }
                    ChartTriStateEnum.MINUTE -> {
                        noMarginBottomAxisItemPlacerForMinute
                    }
                    else -> {
                        noMarginBottomAxisItemPlacer
                    }
                },
                valueFormatter = horizontalAxisValueFormatter,
            ),
            layerPadding = { CartesianLayerPadding() },
            persistentMarkers = {
                peakPoints.forEach { peak ->
                    val marker = if (peak.isFtp) ftpPeakMarker else sftpPeakMarker
                    marker.at(peak.x)
                }
            },
        ),
        modelProducer = modelProducer,
        modifier = modifier,
        // 横向滑动完全由 MainScreen 的手势 + ViewModel 移动数据窗口实现，
        // 关闭 Vico 自己的 scroll/zoom 手势，避免两种手势同时改窗口导致 H 等
        // 较大窗口在拖动时被 Vico 的缩放状态覆盖。
        zoomState = rememberVicoZoomState(
            zoomEnabled = false,
            initialZoom = Zoom.Content,
        ),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        animationSpec = null,
        animateIn = true
    )
}

@Preview(showBackground = true)
@Composable
fun NetworkTrafficChartPreview() {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(Unit) {
        val firstTimestamp = System.currentTimeMillis() / 1000L
        val xValues = (0L..7L).map { firstTimestamp + it }
        modelProducer.runTransaction {
            lineSeries {
                series(xValues, listOf(2, 6, 4, 12, 8, 16, 10, 20))
                series(xValues, listOf(1, 3, 2, 8, 5, 12, 6, 14))
            }
        }
    }

    NetworkTrafficChart(
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    )
}
