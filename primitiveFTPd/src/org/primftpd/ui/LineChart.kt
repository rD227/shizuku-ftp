package org.primftpd.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
//import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

@Composable
fun NetworkTrafficChart(
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier,
) {
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
        ),
        modelProducer = modelProducer,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
fun NetworkTrafficChartPreview() {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(Unit) {
        modelProducer.runTransaction {
            lineSeries {
                series(2, 6, 4, 12, 8, 16, 10, 20)
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
