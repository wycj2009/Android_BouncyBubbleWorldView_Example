package com.example.android_bouncybubbleworldview_example

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import com.example.android_bouncybubbleworldview_example.BouncyBubbleWorldView.Barrier
import com.example.android_bouncybubbleworldview_example.BouncyBubbleWorldView.Bubble
import com.example.android_bouncybubbleworldview_example.BouncyBubbleWorldView.BubbleWorld
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * This view implements bubble animation with raw data updated by simulation in virtual physical world.
 * [Barrier] is placed on the first layout.
 * [Bubble] may be added as a child view of [BouncyBubbleWorldView] inside the [Barrier].
 * If [Bubble] is out of [Barrier], it will be transformed inside [Barrier].
 * Apply gravity and acceleration effects using sensors.
 *
 *   ㅣ         ㅣ   ㅣ
 *   ㅣ         ㅣ   ㅣ
 *   ㅣ         ㅣ   ㅣ <- [Barrier] Bounds (the top of barrier is open)
 *   ㅣ_ _ _ _ _ㅣ   ㅣ
 *   ㅣ  o  o   ㅣ   ㅣ   ㅣ
 *   ㅣ   o  o  ㅣ   ㅣ   ㅣ
 *   ㅣ o  o   oㅣ   ㅣ   ㅣ <- [BouncyBubbleWorldView] Bounds
 *   ㅣ_ _ _ _ _ㅣ   ㅣ   ㅣ
 *   [BubbleWorld]
 */
class BouncyBubbleWorldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : ViewGroup(context, attrs, defStyleAttr, defStyleRes) {
    var isInitialized: Boolean = false
        private set
    var onInitialized: ((view: BouncyBubbleWorldView) -> Unit)? = null
        set(value) {
            if (isInitialized) {
                value?.invoke(this)
            }
            field = value
        }
    var isDebuggingMode: Boolean = false
    val barrierWidth: Float
        get() = barrier.width
    val barrierHeight: Float
        get() = barrier.height
    private val bubbleWorld: BubbleWorld = BubbleWorld {
        requestLayout()
    }
    private lateinit var barrier: Barrier
    private val bubbles: MutableList<Bubble> = mutableListOf()
    private val sensor = object {
        private val manager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val gravity = object {
            val sensor: android.hardware.Sensor = manager.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY) as android.hardware.Sensor
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    x = event.values[0]
                    y = event.values[1]
                    onSensorChanged()
                }

                override fun onAccuracyChanged(sensor: android.hardware.Sensor, accuracy: Int) {
                }
            }
            var x: Float = 0f
            var y: Float = 0f
        }
        private val linearAcceleration = object {
            val sensor: android.hardware.Sensor = manager.getDefaultSensor(android.hardware.Sensor.TYPE_LINEAR_ACCELERATION) as android.hardware.Sensor
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    x = event.values[0]
                    y = event.values[1]
                    onSensorChanged()
                }

                override fun onAccuracyChanged(sensor: android.hardware.Sensor, accuracy: Int) {
                }
            }
            var x: Float = 0f
            var y: Float = 0f
        }

        fun register() {
            manager.registerListener(gravity.listener, gravity.sensor, SensorManager.SENSOR_DELAY_UI)
            manager.registerListener(linearAcceleration.listener, linearAcceleration.sensor, SensorManager.SENSOR_DELAY_UI)
        }

        fun unregister() {
            manager.unregisterListener(gravity.listener, gravity.sensor)
            manager.unregisterListener(linearAcceleration.listener, linearAcceleration.sensor)
        }

        private fun onSensorChanged() {
            val gravityX: Float = -((gravity.x * Sensor.GRAVITY_CORRECTION_VALUE) + (linearAcceleration.x * Sensor.LINEAR_ACCELERATION_CORRECTION_VALUE))
            val gravityY: Float = (gravity.y * Sensor.GRAVITY_CORRECTION_VALUE) + (linearAcceleration.y * Sensor.LINEAR_ACCELERATION_CORRECTION_VALUE)
            bubbleWorld.updateGravity(gravityX, gravityY)
        }
    }

    init {
        doOnLayout {
            barrier = Barrier(
                width = this.measuredWidth.toFloat(),
                height = this.measuredHeight * 2f,
                centerX = this.measuredWidth * 0.5f,
                centerY = 0f,
                destCenterX = this.measuredWidth * 0.5f,
                destCenterY = 0f
            )
            bubbleWorld.initBarrier(barrier)
            isInitialized = true
            onInitialized?.invoke(this)
        }
        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                sensor.register()
                bubbleWorld.startSimulation()
            }

            override fun onViewDetachedFromWindow(v: View) {
                sensor.unregister()
                bubbleWorld.cancelSimulation()
            }
        })
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        bubbles.forEach {
            it.view.measure(
                MeasureSpec.makeMeasureSpec((it.radius * 2f).roundToInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((it.radius * 2f).roundToInt(), MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        bubbles.forEach {
            val left: Int = (it.centerX - it.radius).roundToInt()
            val top: Int = (it.centerY - it.radius).roundToInt()
            val diameter: Int = (it.radius * 2f).roundToInt()
            it.view.layout(left, top, left + diameter, top + diameter)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        // Draw barrier
        if (isDebuggingMode) {
            canvas.drawRect(
                barrier.centerX - (barrier.width * 0.5f),
                barrier.centerY - (barrier.height * 0.5f),
                barrier.centerX + (barrier.width * 0.5f),
                barrier.centerY + (barrier.height * 0.5f),
                barrier.paint
            )
        }
    }

    fun addBubbles(intervalMillis: Long, vararg bubble: Bubble) {
        CoroutineScope(Dispatchers.Main).launch {
            bubble.forEach { bubble: Bubble ->
                delay(intervalMillis)

                bubbles.add(bubble)
                bubbleWorld.createBubble(bubble)
                this@BouncyBubbleWorldView.addView(bubble.view)
            }
        }
    }

    fun moveBarrier(destCenterX: Float, destCenterY: Float) {
        barrier.let {
            it.destCenterX = destCenterX
            it.destCenterY = destCenterY
            bubbleWorld.moveBarrier(it)
        }
    }

    private companion object {
        private const val SCREEN_TO_WORLD_RATIO: Float = 2000f // Defines how many pixels correspond to 1 meter.

        private object Sensor {
            const val GRAVITY_CORRECTION_VALUE: Float = 0.6f
            const val LINEAR_ACCELERATION_CORRECTION_VALUE: Float = 2.4f
        }

        private object Body {
            const val FRICTION: Float = 0.4f // The friction coefficient, usually in the range [0,1].
            const val RESTITUTION: Float = 0.6f // The restitution (elasticity) usually in the range [0,1].
            const val DENSITY: Float = 1f // The density, usually in kg/m^2.
            const val LINEAR_DAMPING: Float = 1f // This limits the maximum speed.
            const val INITIAL_IMPULSE: Float = 0.005f // The world impulse vector, usually in N-seconds or kg-m/s.
        }

        private object Simulation {
            const val TARGET_FPS: Float = 120f
            const val TIME_STEP: Float = 1f / TARGET_FPS
            const val TIME_STEP_MILLIS: Long = (TIME_STEP * 1000L).toLong()
        }
    }

    /**
     * This is the Box2d physics world.
     * It runs the simulation at a constant interval and notifies the listener at each simulation step.
     */
    class BubbleWorld(
        private val onSimulationUpdated: () -> Unit
    ) {
        private val world = World(Vec2(0f, 9.8f), false)
        private lateinit var barrierBody: org.jbox2d.dynamics.Body
        private var simulationJob: Job? = null

        init {
            destroyWorld()
        }

        fun startSimulation() {
            simulationJob?.cancel()
            simulationJob = CoroutineScope(Dispatchers.Main).launch {
                while (true) {
                    delay(Simulation.TIME_STEP_MILLIS)

                    // Update physics world
                    world.step(Simulation.TIME_STEP, 2, 2)
                    var body: org.jbox2d.dynamics.Body? = world.bodyList
                    while (body != null) {
                        when (val userData: Any? = body.userData) {
                            is Barrier -> {
                                body.linearVelocity = body.linearVelocity.set(0f, 0f)
                                userData.centerX = body.position.x.metersToPixels()
                                userData.centerY = body.position.y.metersToPixels()
                                userData.destCenterX = userData.centerX
                                userData.destCenterY = userData.centerY
                            }
                            is Bubble -> {
                                userData.centerX = body.position.x.metersToPixels()
                                userData.centerY = body.position.y.metersToPixels()
                                if (userData.isOutOfBarrier()) {
                                    val coercedPosition: Vec2 = userData.getPositionWhenOutOfBarrier()
                                    body.setTransform(coercedPosition, userData.radius)
                                    userData.centerX = coercedPosition.x.metersToPixels()
                                    userData.centerY = coercedPosition.y.metersToPixels()
                                }
                                userData.rotation = Math.toDegrees(body.angle.toDouble()).toFloat()
                                userData.view.rotation = userData.rotation
                            }
                        }
                        body = body.next
                    }
                    onSimulationUpdated.invoke()
                }
            }
        }

        fun cancelSimulation() {
            simulationJob?.cancel()
        }

        fun initBarrier(barrier: Barrier) {
            if (this::barrierBody.isInitialized) return

            val bodyDef = BodyDef().apply {
                type = BodyType.KINEMATIC
                position.set(barrier.centerX.pixelsToMeters(), barrier.centerY.pixelsToMeters())
            }
            val barrierHalfWidth: Float = barrier.width.pixelsToMeters() * 0.5f
            val barrierHalfHeight: Float = barrier.height.pixelsToMeters() * 0.5f
            val fixtureDefLeft = FixtureDef().apply {
                shape = PolygonShape().apply {
                    setAsEdge(
                        Vec2(-barrierHalfWidth, barrierHalfHeight),
                        Vec2(-barrierHalfWidth, -barrierHalfHeight)
                    )
                }
                friction = Body.FRICTION
                restitution = Body.RESTITUTION
                density = Body.DENSITY
            }
            val fixtureDefRight = FixtureDef().apply {
                shape = PolygonShape().apply {
                    setAsEdge(
                        Vec2(barrierHalfWidth, -barrierHalfHeight),
                        Vec2(barrierHalfWidth, barrierHalfHeight)
                    )
                }
                friction = Body.FRICTION
                restitution = Body.RESTITUTION
                density = Body.DENSITY
            }
            val fixtureDefBottom = FixtureDef().apply {
                shape = PolygonShape().apply {
                    setAsEdge(
                        Vec2(barrierHalfWidth, barrierHalfHeight),
                        Vec2(-barrierHalfWidth, barrierHalfHeight)
                    )
                }
                friction = Body.FRICTION
                restitution = Body.RESTITUTION
                density = Body.DENSITY
            }
            barrierBody = world.createBody(bodyDef).apply {
                createFixture(fixtureDefLeft)
                createFixture(fixtureDefRight)
                createFixture(fixtureDefBottom)
                userData = barrier
            }
        }

        fun moveBarrier(barrier: Barrier) {
            val destCenterXPx: Float = barrier.destCenterX - barrier.centerX // Barrier.centerX will be destCenterXPx on the next simulation updating.
            val destCenterYPx: Float = barrier.destCenterY - barrier.centerY // Barrier.centerY will be destCenterYPx on the next simulation updating.
            val velocityX: Float = (destCenterXPx * Simulation.TARGET_FPS).pixelsToMeters()
            val velocityY: Float = (destCenterYPx * Simulation.TARGET_FPS).pixelsToMeters()
            barrierBody.linearVelocity = barrierBody.linearVelocity.set(velocityX, velocityY)
        }

        fun createBubble(bubble: Bubble) {
            val bodyDef = BodyDef().apply {
                type = BodyType.DYNAMIC
                position.set(bubble.getPositionWhenCreate())
            }
            val fixtureDef = FixtureDef().apply {
                shape = CircleShape().apply {
                    m_radius = bubble.radius.pixelsToMeters()
                }
                friction = Body.FRICTION
                restitution = Body.RESTITUTION
                density = Body.DENSITY
            }
            world.createBody(bodyDef).let {
                it.createFixture(fixtureDef)
                it.m_mass = Math.PI.toFloat() * fixtureDef.shape.m_radius
                it.userData = bubble
                it.linearDamping = Body.LINEAR_DAMPING
                val impulse = Vec2(((Random.nextFloat() * 2f) - 1f) * Body.INITIAL_IMPULSE, Body.INITIAL_IMPULSE)
                it.applyLinearImpulse(impulse, it.position) // Give the body an initial speed.
            }
        }

        fun updateGravity(gravityX: Float, gravityY: Float) {
            world.gravity = world.gravity.set(gravityX, gravityY)
        }

        private fun destroyWorld() {
            var body: org.jbox2d.dynamics.Body? = world.bodyList
            while (body != null) {
                world.destroyBody(body)
                body = body.next
            }
        }

        private fun Bubble.getPositionWhenCreate(): Vec2 {
            val barrier: Barrier = barrierBody.userData as Barrier
            val centerX: Float = (barrier.centerX + ((barrier.width - (this.radius * 2f)) * (Random.nextFloat() - 0.5f))).pixelsToMeters()
            val centerY: Float = -this.radius.pixelsToMeters()
            return Vec2(centerX, centerY)
        }

        private fun Bubble.getPositionWhenOutOfBarrier(): Vec2 {
            val barrier: Barrier = barrierBody.userData as Barrier
            val halfBarrierWidth: Float = barrier.width * 0.5f
            val centerX: Float = this.centerX.also {
                it.coerceAtLeast(barrier.centerX - halfBarrierWidth + this.radius)
                it.coerceAtMost(barrier.centerX + halfBarrierWidth - this.radius)
            }.pixelsToMeters()
            val centerY: Float = (barrier.centerY - (barrier.height * 0.5f) + this.radius).pixelsToMeters()
            return Vec2(centerX, centerY)
        }

        private fun Bubble.isOutOfBarrier(): Boolean {
            val barrier: Barrier = barrierBody.userData as Barrier
            val barrierHalfWidth: Float = barrier.width * 0.5f
            val barrierHalfHeight: Float = barrier.height * 0.5f
            return (this.centerX in barrier.run { centerX - barrierHalfWidth..centerX + barrierHalfWidth } &&
                this.centerY in barrier.run { centerY - barrierHalfHeight..centerY + barrierHalfHeight }).not()
        }

        private fun Float.pixelsToMeters(): Float {
            return this / SCREEN_TO_WORLD_RATIO
        }

        private fun Float.metersToPixels(): Float {
            return this * SCREEN_TO_WORLD_RATIO
        }
    }

    data class Barrier(
        val width: Float,
        val height: Float,
        var centerX: Float,
        var centerY: Float,
        var destCenterX: Float,
        var destCenterY: Float
    ) {
        val paint: Paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.RED
        }
    }

    data class Bubble(
        val view: View,
        val radius: Float
    ) {
        var centerX: Float = -10_000_000f
        var centerY: Float = -10_000_000f
        var rotation: Float = 0f
    }
}
