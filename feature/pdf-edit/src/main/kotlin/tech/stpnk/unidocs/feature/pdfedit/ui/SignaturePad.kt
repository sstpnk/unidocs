package tech.stpnk.unidocs.feature.pdfedit.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Пад для рисования факсимиле пальцем. По «Готово» возвращает прозрачный PNG-битмап
 * с обрезкой по содержимому.
 */
@Composable
fun SignaturePad(modifier: Modifier = Modifier, onReady: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<MutableList<Offset>>() }
    var current by remember { mutableStateOf<MutableList<Offset>?>(null) }
    var invalidator by remember { mutableIntStateOf(0) }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val stroke = mutableListOf(offset)
                            strokes.add(stroke)
                            current = stroke
                            invalidator++
                        },
                        onDrag = { change, _ ->
                            current?.add(change.position)
                            invalidator++
                        },
                        onDragEnd = {
                            current = null
                            invalidator++
                        },
                        onDragCancel = {
                            current = null
                            invalidator++
                        },
                    )
                },
        ) {
            // чтение invalidator заставляет канву перерисовываться
            @Suppress("UNUSED_EXPRESSION") invalidator
            val paint = Paint().apply {
                color = android.graphics.Color.BLACK
                strokeWidth = 5f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            drawIntoCanvas { canvas ->
                strokes.forEach { stroke ->
                    if (stroke.size > 1) {
                        val path = Path().apply {
                            moveTo(stroke.first().x, stroke.first().y)
                            stroke.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        canvas.nativeCanvas.drawPath(path, paint)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TextButton(onClick = { strokes.clear(); invalidator++ }) { Text("Очистить") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    renderSignatureBitmap(strokes)?.let(onReady)
                },
                enabled = strokes.isNotEmpty(),
            ) {
                Text("Готово")
            }
        }
    }
}

private fun renderSignatureBitmap(strokes: List<List<Offset>>): Bitmap? {
    val points = strokes.flatten()
    if (points.isEmpty()) return null
    val pad = 16f
    val minX = (points.minOf { it.x } - pad).coerceAtLeast(0f)
    val minY = (points.minOf { it.y } - pad).coerceAtLeast(0f)
    val maxX = points.maxOf { it.x } + pad
    val maxY = points.maxOf { it.y } + pad
    val width = (maxX - minX).toInt().coerceIn(32, 4096)
    val height = (maxY - minY).toInt().coerceIn(32, 4096)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    strokes.forEach { stroke ->
        if (stroke.size > 1) {
            val path = Path()
            path.moveTo(stroke.first().x - minX, stroke.first().y - minY)
            stroke.drop(1).forEach { path.lineTo(it.x - minX, it.y - minY) }
            canvas.drawPath(path, paint)
        }
    }
    return bitmap
}
