package com.example.engine3d

import android.content.Context
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class NeonfallSurfaceView(
    context: Context,
    sim: GameSimulation,
    onHudTick: (HudSnapshot) -> Unit
) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(3)
        setRenderer(NeonfallGLRenderer(sim, onHudTick, context.assets))
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}

@Composable
fun Neonfall3DViewport(
    sim: GameSimulation,
    onHudTick: (HudSnapshot) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            NeonfallSurfaceView(ctx, sim, onHudTick)
        }
    )
}
