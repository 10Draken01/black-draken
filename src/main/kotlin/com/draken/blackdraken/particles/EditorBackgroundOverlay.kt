package com.draken.blackdraken.particles

import com.draken.blackdraken.settings.BlackDrakenSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaListener
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.Timer
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class EditorBackgroundOverlay(
    private val editor: Editor,
    parentDisposable: Disposable
) : JComponent() {

    private var glitch = 0f
    private var glitchEndsAt = 0L
    private var nextGlitchAt =
        System.currentTimeMillis() + Random.nextLong(1500, 3500)

    // Caché de la imagen escalada al tamaño actual
    private var cache: BufferedImage? = null
    private var cacheW = 0
    private var cacheH = 0

    private val frameTimer = Timer(FRAME_MS) { tick() }

    private val rescaleTimer = Timer(RESCALE_DELAY_MS) {
        rebuildCache()
        repaint()
    }.apply { isRepeats = false }

    private val resizeListener = object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
            syncBounds()
        }
    }

    init {
        isOpaque = false
        isFocusable = false

        editor.contentComponent.add(this)
        editor.contentComponent.addComponentListener(resizeListener)

        // Scroll / resize del panel del editor: la imagen sigue al viewport
        editor.scrollingModel.addVisibleAreaListener(
            VisibleAreaListener { repaint() },
            parentDisposable
        )

        syncBounds()
        frameTimer.start()
    }

    override fun contains(x: Int, y: Int): Boolean = false

    /** Dispara un glitch corto (por ejemplo, al pulsar Enter). */
    fun kick() {
        val now = System.currentTimeMillis()
        glitchEndsAt = now + Random.nextLong(120, 260)
        nextGlitchAt = glitchEndsAt + Random.nextLong(2000, 5000)
    }

    private fun settings() = BlackDrakenSettings.getInstance().state

    private fun syncBounds() {
        val c = editor.contentComponent
        if (bounds.width != c.width || bounds.height != c.height) {
            setBounds(0, 0, c.width, c.height)
        }
        repaint()
    }

    private fun tick() {
        if (editor.isDisposed || !isShowing) return

        val s = settings()
        if (!s.backgroundEnabled || !s.glitchEnabled) {
            if (glitchEndsAt != 0L) {
                glitchEndsAt = 0L
                glitch = 0f
                repaint()
            }
            return
        }

        val now = System.currentTimeMillis()

        if (glitchEndsAt == 0L && now >= nextGlitchAt) {
            glitchEndsAt = now + Random.nextLong(180, 520)
        }

        if (glitchEndsAt != 0L) {
            if (now >= glitchEndsAt) {
                glitchEndsAt = 0L
                glitch = 0f
                nextGlitchAt = now + Random.nextLong(2200, 6500)
            } else {
                glitch = 0.35f + Random.nextFloat() * 0.65f
            }
            repaint()
        }
    }

    // ---------------------------------------------------------------- escalado

    private fun rebuildCache() {
        val src = BlackDrakenImages.demonImage ?: return
        val b = calculateImageBounds(editor.scrollingModel.visibleArea.width, editor.scrollingModel.visibleArea.height, src)
        val w = b.width.roundToInt().coerceAtLeast(1)
        val h = b.height.roundToInt().coerceAtLeast(1)

        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC
        )
        g.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY
        )
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()

        cache?.flush()
        cache = out
        cacheW = w
        cacheH = h
    }

    private fun calculateImageBounds(
        containerW: Int,
        containerH: Int,
        image: BufferedImage
    ): Rectangle2D.Float {
        val maxW = containerW * IMAGE_WIDTH_RATIO
        val maxH = containerH * IMAGE_HEIGHT_RATIO
        val ratio = image.width.toFloat() / image.height.toFloat()

        var w = maxW
        var h = w / ratio
        if (h > maxH) {
            h = maxH
            w = h * ratio
        }

        return Rectangle2D.Float(
            (containerW - w) / 2f,
            (containerH - h) / 2f,
            w,
            h
        )
    }

    // ------------------------------------------------------------------ pintado

    override fun paintComponent(g: Graphics) {
        val s = settings()
        if (!s.backgroundEnabled) return

        val src = BlackDrakenImages.demonImage ?: return

        // Solo el área visible del editor (funciona con scroll y resize)
        val area = editor.scrollingModel.visibleArea
        if (area.width <= 0 || area.height <= 0) return

        val g2 = g.create() as Graphics2D
        try {
            g2.clipRect(area.x, area.y, area.width, area.height)
            g2.translate(area.x, area.y)

            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            )

            val b = calculateImageBounds(area.width, area.height, src)
            val bw = b.width.roundToInt().coerceAtLeast(1)
            val bh = b.height.roundToInt().coerceAtLeast(1)

            val baseAlpha = (s.backgroundOpacity.coerceIn(1, 40)) / 100f

            // Imagen escalada en caché; si aún no coincide, pintamos rápido
            // con bilinear y programamos el reescalado de calidad.
            val img: Image
            val iw: Int
            val ih: Int
            if (cache != null && cacheW == bw && cacheH == bh) {
                img = cache!!
                iw = cacheW
                ih = cacheH
            } else {
                rescaleTimer.restart()
                g2.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
                )
                img = src
                iw = src.width
                ih = src.height
            }

            drawAura(g2, area.width, area.height, baseAlpha)

            g2.composite = alpha(baseAlpha)
            g2.drawImage(
                img,
                b.x.toInt(), b.y.toInt(),
                (b.x + b.width).toInt(), (b.y + b.height).toInt(),
                0, 0, iw, ih,
                null
            )

            if (glitch > 0.08f && s.glitchEnabled) {
                drawGlitch(g2, img, iw, ih, b, glitch, baseAlpha)
                drawScanlines(g2, b, glitch)
            }
        } finally {
            g2.dispose()
        }
    }

    /** Aura roja que sube desde abajo, como maná demoníaco. */
    private fun drawAura(g: Graphics2D, w: Int, h: Int, baseAlpha: Float) {
        val strength = (0.10f + baseAlpha * 0.6f + glitch * 0.10f).coerceAtMost(0.4f)
        g.composite = AlphaComposite.SrcOver
        g.paint = GradientPaint(
            0f, h.toFloat(),
            Color(255, 23, 68, (strength * 255).toInt()),
            0f, h * 0.55f,
            Color(255, 23, 68, 0)
        )
        g.fillRect(0, (h * 0.45f).toInt(), w, (h * 0.55f).toInt() + 1)
    }

    private fun drawGlitch(
        g: Graphics2D,
        img: Image,
        iw: Int,
        ih: Int,
        b: Rectangle2D.Float,
        level: Float,
        baseAlpha: Float
    ) {
        val slices = 3 + (level * 9).toInt()
        val sliceAlpha = (baseAlpha * (1.5f + 2f * level)).coerceAtMost(0.6f)

        repeat(slices) {
            val top = Random.nextFloat() * b.height
            val sh = (Random.nextFloat() * 0.05f * b.height).coerceAtLeast(2f)
            val bottom = min(top + sh, b.height)
            val shift = (Random.nextFloat() - 0.5f) * b.width * 0.16f * level

            val sy1 = (top / b.height * ih).toInt().coerceIn(0, ih - 1)
            val sy2 = (bottom / b.height * ih).toInt().coerceIn(sy1 + 1, ih)

            // Franja de la imagen desplazada
            g.composite = alpha(sliceAlpha)
            g.drawImage(
                img,
                (b.x + shift).toInt(), (b.y + top).toInt(),
                (b.x + b.width + shift).toInt(), (b.y + bottom).toInt(),
                0, sy1, iw, sy2,
                null
            )

            // Barra neón
            if (Random.nextInt(100) < 35) {
                g.composite = alpha(0.08f + 0.20f * level)
                g.color = NEON
                g.fillRect(
                    (b.x + shift).toInt(),
                    (b.y + top).toInt(),
                    b.width.toInt(),
                    ((bottom - top) / 3f).toInt().coerceAtLeast(1)
                )
            }
        }

        // Bloque "corrupto" negro
        if (Random.nextInt(100) < 40) {
            g.composite = alpha(0.55f)
            g.color = VOID
            val bx = b.x + Random.nextFloat() * b.width * 0.8f
            val by = b.y + Random.nextFloat() * b.height
            g.fillRect(
                bx.toInt(), by.toInt(),
                (b.width * (0.04f + Random.nextFloat() * 0.12f)).toInt(),
                (4 + Random.nextInt(10))
            )
        }
    }

    private fun drawScanlines(g: Graphics2D, b: Rectangle2D.Float, level: Float) {
        g.composite = alpha(0.035f * level)
        g.color = NEON
        var y = b.y.toInt()
        val end = (b.y + b.height).toInt()
        while (y < end) {
            g.fillRect(b.x.toInt(), y, b.width.toInt(), 1)
            y += 4
        }
    }

    private fun alpha(a: Float): AlphaComposite =
        AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a.coerceIn(0f, 1f))

    fun dispose() {
        frameTimer.stop()
        rescaleTimer.stop()
        editor.contentComponent.removeComponentListener(resizeListener)
        editor.contentComponent.remove(this)
        editor.contentComponent.repaint()
        cache?.flush()
        cache = null
    }

    private companion object {
        const val FRAME_MS = 33
        const val RESCALE_DELAY_MS = 150

        // Tamaño máximo de la imagen dentro del viewport
        const val IMAGE_WIDTH_RATIO = 0.55f
        const val IMAGE_HEIGHT_RATIO = 0.75f

        val NEON = Color(0xFF1744)
        val VOID = Color(0x050505)
    }
}