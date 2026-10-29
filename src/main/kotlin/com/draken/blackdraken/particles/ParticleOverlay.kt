package com.draken.blackdraken.particles

import com.intellij.openapi.editor.Editor
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.Timer
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class ParticleOverlay(
    private val editor: Editor
) : JComponent() {

    private enum class Type { GLOW, EMBER, SPARK, SLASH }

    private class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        val maxLife: Float,
        val size: Float,
        val color: Color,
        val type: Type,
        val angle: Float = 0f
    )

    private val particles = ArrayList<Particle>()

    private val timer = Timer(FRAME_MS) { update() }

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
        syncBounds()
    }

    override fun contains(x: Int, y: Int): Boolean = false

    private fun syncBounds() {
        val c = editor.contentComponent
        setBounds(0, 0, c.width, c.height)
    }

    /** Brasas y destellos al escribir. */
    fun emit(x: Int, y: Int, intensity: Int) {
        if (editor.isDisposed) return

        val amount = when {
            intensity <= 2 -> 1
            intensity <= 5 -> 2
            intensity <= 8 -> 3
            else -> 4
        }

        repeat(amount) {
            if (particles.size >= MAX_PARTICLES) return@repeat

            val life = Random.nextDouble(450.0, 900.0).toFloat()
            val type = when (Random.nextInt(100)) {
                in 0..34 -> Type.GLOW
                in 35..74 -> Type.EMBER
                else -> Type.SPARK
            }

            particles.add(
                Particle(
                    x = x + Random.nextFloat() * 8f - 4f,
                    y = y + Random.nextFloat() * 8f - 4f,
                    vx = Random.nextFloat() * 1.2f - 0.6f,
                    vy = -(Random.nextFloat() * 1.4f + 0.5f),
                    life = life,
                    maxLife = life,
                    size = Random.nextFloat() * 2.8f + 1.0f,
                    color = COLORS.random(),
                    type = type
                )
            )
        }

        startTimer()
    }

    /** Tajo diagonal de la espada anti-magia (al pulsar Enter). */
    fun slash(x: Int, y: Int) {
        if (editor.isDisposed) return

        particles.add(
            Particle(
                x = x.toFloat(),
                y = y.toFloat(),
                vx = 0f,
                vy = 0f,
                life = 280f,
                maxLife = 280f,
                size = 2.2f,
                color = NEON,
                type = Type.SLASH,
                angle = (-35.0 + Random.nextDouble(-12.0, 12.0)).toFloat()
            )
        )

        repeat(5) {
            if (particles.size >= MAX_PARTICLES + 10) return@repeat
            val life = Random.nextDouble(300.0, 650.0).toFloat()
            particles.add(
                Particle(
                    x = x.toFloat(),
                    y = y.toFloat(),
                    vx = Random.nextFloat() * 3.2f - 1.6f,
                    vy = -(Random.nextFloat() * 2.2f + 0.4f),
                    life = life,
                    maxLife = life,
                    size = Random.nextFloat() * 2f + 1f,
                    color = COLORS.random(),
                    type = Type.SPARK
                )
            )
        }

        startTimer()
    }

    private fun startTimer() {
        if (!timer.isRunning) timer.start()
        repaint()
    }

    private fun update() {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()

            p.x += p.vx
            p.y += p.vy
            p.vx *= 0.985f
            p.vy *= 0.985f

            when (p.type) {
                Type.EMBER -> {
                    p.vx += (Random.nextFloat() - 0.5f) * 0.10f   // vaivén
                    p.vy -= 0.012f                                // asciende
                }
                Type.GLOW, Type.SPARK -> p.vy -= 0.006f
                Type.SLASH -> Unit
            }

            p.life -= FRAME_MS
            if (p.life <= 0f) it.remove()
        }

        if (particles.isEmpty()) timer.stop()
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        if (particles.isEmpty()) return

        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            )

            for (p in particles) {
                val a = (p.life / p.maxLife).coerceIn(0f, 1f)
                when (p.type) {
                    Type.GLOW -> drawGlow(g2, p, a)
                    Type.EMBER -> drawEmber(g2, p, a)
                    Type.SPARK -> drawSpark(g2, p, a)
                    Type.SLASH -> drawSlash(g2, p, a)
                }
            }
        } finally {
            g2.dispose()
        }
    }

    private fun alpha(g: Graphics2D, a: Float) {
        g.composite = AlphaComposite.getInstance(
            AlphaComposite.SRC_OVER,
            a.coerceIn(0f, 1f)
        )
    }

    private fun drawGlow(g: Graphics2D, p: Particle, a: Float) {
        glowLayer(g, p, a * 0.08f, 7f)
        glowLayer(g, p, a * 0.16f, 4f)
        glowLayer(g, p, a * 0.9f, 1.4f)
    }

    private fun glowLayer(g: Graphics2D, p: Particle, a: Float, radius: Float) {
        alpha(g, a)
        g.color = p.color
        val s = p.size * radius
        g.fill(Ellipse2D.Float(p.x - s / 2f, p.y - s / 2f, s, s))
    }

    /** Brasa que parpadea mientras sube. */
    private fun drawEmber(g: Graphics2D, p: Particle, a: Float) {
        val flicker = 0.65f + 0.35f * sin(p.life * 0.05f)
        val s = p.size * 1.6f

        alpha(g, a * 0.18f * flicker)
        g.color = p.color
        g.fill(Ellipse2D.Float(p.x - s * 1.8f, p.y - s * 1.8f, s * 3.6f, s * 3.6f))

        alpha(g, a * flicker)
        g.fill(Rectangle2D.Float(p.x - s / 2f, p.y - s / 2f, s, s))
    }

    private fun drawSpark(g: Graphics2D, p: Particle, a: Float) {
        val len = p.size * 5f

        alpha(g, a * 0.15f)
        g.color = p.color
        g.stroke = BasicStroke(p.size * 2f)
        g.draw(Line2D.Float(p.x - len, p.y, p.x + len, p.y))

        alpha(g, a)
        g.stroke = BasicStroke(0.7f)
        g.draw(Line2D.Float(p.x - len, p.y, p.x + len, p.y))
        g.draw(Line2D.Float(p.x, p.y - len, p.x, p.y + len))
    }

    /** Corte diagonal con núcleo blanco y halo rojo neón. */
    private fun drawSlash(g: Graphics2D, p: Particle, a: Float) {
        val progress = 1f - a
        val len = SLASH_LENGTH * (0.4f + 0.6f * min(1f, progress * 4f))
        val rad = Math.toRadians(p.angle.toDouble())
        val dx = (cos(rad) * len).toFloat()
        val dy = (sin(rad) * len).toFloat()

        val line = Line2D.Float(p.x - dx, p.y - dy, p.x + dx, p.y + dy)

        alpha(g, a * 0.25f)
        g.color = NEON
        g.stroke = BasicStroke(p.size * 4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(line)

        alpha(g, a * 0.9f)
        g.color = Color.WHITE
        g.stroke = BasicStroke(p.size * 0.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(line)
    }

    fun dispose() {
        timer.stop()
        particles.clear()
        editor.contentComponent.removeComponentListener(resizeListener)
        editor.contentComponent.remove(this)
        editor.contentComponent.repaint()
    }

    private companion object {
        const val MAX_PARTICLES = 60
        const val FRAME_MS = 33
        const val SLASH_LENGTH = 42f

        val NEON = Color(0xFF1744)

        val COLORS = arrayOf(
            Color(0xFF1744),
            Color(0xFF1744),
            Color(0xFF5252),
            Color(0xC11235),
            Color(0x7A1025),
            Color(0xFFFFFF)
        )
    }
}