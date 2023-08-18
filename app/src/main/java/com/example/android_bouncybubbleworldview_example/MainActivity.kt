package com.example.android_bouncybubbleworldview_example

import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import com.example.android_bouncybubbleworldview_example.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bouncyBubbleWorldView.run {
            isDebuggingMode = true
            onInitialized = { view: BouncyBubbleWorldView ->
                val bubbles = mutableListOf<BouncyBubbleWorldView.Bubble>().apply {
                    repeat(15) {
                        add(
                            BouncyBubbleWorldView.Bubble(
                                view = TextView(this@MainActivity).apply {
                                    background = ShapeDrawable(OvalShape()).apply {
                                        paint.color = Color.BLACK
                                    }
                                    gravity = Gravity.CENTER
                                    setTextColor(Color.WHITE)
                                    text = "${it}"
                                },
                                radius = 42.5f.dpToPx
                            )
                        )
                    }
                }
                view.addBubbles(200L, *bubbles.toTypedArray())
            }
        }
        binding.bottomSheetContainerView.run {
            doOnLayout {
                moveBarrier()
            }
            onBottomSheetHeightChange = { height: Int ->
                moveBarrier()
            }
        }
    }

    private fun moveBarrier() {
        binding.bouncyBubbleWorldView.let {
            it.moveBarrier(
                destCenterX = it.barrierWidth * 0.5f,
                destCenterY = (it.height - binding.bottomSheetContainerView.bottomSheet.height) - (it.barrierHeight * 0.5f)
            )
        }
    }
}
