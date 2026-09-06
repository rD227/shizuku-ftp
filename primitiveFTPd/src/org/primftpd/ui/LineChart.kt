package org.primftpd.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
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



@Composable
fun NetworkTrafficChart(
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier,
    animateModelChanges: Boolean = false,
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
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
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
        ),
        modelProducer = modelProducer,
        modifier = modifier,
        // 禁止缩放及滑动。这里不使用 Vico 的模型差值动画，避免切换刻度时
        // 在数据窗口变化/补零过程中触发崩溃；FourStateSwitch 的滑块仍有自身动画。
        zoomState = rememberVicoZoomState(
            zoomEnabled = true,
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
