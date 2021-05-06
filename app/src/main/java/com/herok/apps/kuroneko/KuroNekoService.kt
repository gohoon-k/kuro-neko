package com.herok.apps.kuroneko

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.os.Handler
import android.service.wallpaper.WallpaperService
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.animation.DecelerateInterpolator
import androidx.preference.PreferenceManager

class KuroNekoService: WallpaperService() {

    companion object {

        const val ACTION_LOOK_AT: Byte = 0
        const val ACTION_HALF_EYES: Byte = 1
        const val ACTION_ANGRY: Byte = 2
        const val ACTION_BLINK: Byte = 3
        const val ACTION_DORIDORI: Byte = 4
        const val ACTION_SMELLING: Byte = 5

        const val EYE_TYPE_NORMAL: Byte = 0
        const val EYE_TYPE_HALF: Byte = 1
        const val EYE_TYPE_ANGRY: Byte = 2
        const val EYE_TYPE_BLINK: Byte = -1

    }

    override fun onCreateEngine(): Engine = KuroNekoEngine(this)

    private inner class KuroNekoEngine(context: Context): Engine(){

        val prefs: SharedPreferences

        //val types: Array<Byte> = arrayOf(1, 1)
        val types: Array<Byte> = arrayOf(2, 2, 1, 1, 0, 0, 0, 0, 0, 0, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3)
        val delays = arrayOf(2000, 5000, 5000, 5000, 5000, 8000, 8000, 8000, 8000, 12000)

        val handler = Handler(mainLooper)
        val drawRunner = Runnable { draw() }
        val performActionRunner = Runnable { performAction(nextAction) }
        val finishActionRunner = Runnable { finishAction() }

        var visible = false

        var width = 0
        var height = 0

        lateinit var back: Bitmap
        val brightBack: Bitmap
        val darkBack: Bitmap
        val nose: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.kuroneko_nose)
        val su: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.kuroneko_su)

        lateinit var backRect: Rect
        lateinit var noseRect: Rect
        lateinit var suRect: Rect

        val eyeBackPaint = Paint()
        val eyePaint = Paint()
        val eyeCoverPaint = Paint()
        val eyebrowPaint = Paint()
        val eyeBackSize = context.resources.getDimensionPixelSize(R.dimen.eye_back_size).toFloat() // 75f
        val eyeSize = context.resources.getDimensionPixelSize(R.dimen.eye_size).toFloat() // 56f
        val eyebrowHeight = context.resources.getDimensionPixelSize(R.dimen.eyebrow_height).toFloat() // 50f

        var bgColor: Int = 0

        var eyeType: Byte = 0

        var useText: Boolean = false
        lateinit var text: String
        val textPaint = TextPaint()
        val textTypeface: Typeface = Typeface.createFromAsset(context.assets, "fonts/JetBrainsMono-Regular.ttf")
        lateinit var textLayoutBuilder: StaticLayout.Builder
        lateinit var textLayout: StaticLayout

        var leftEyePosition = arrayOf(0f, 0f)
        var rightEyePosition = arrayOf(0f, 0f)
        var nosePosition = arrayOf(0f, 0f)
        var suPosition = arrayOf(0f, 0f)

        var eyesBackLookingAt = arrayOf(
            arrayOf(0f, 0f),        // left eye back(x, y)
            arrayOf(0f, 0f)         // right eye back(x, y)
        )

        var eyesLookingAt = arrayOf(
            arrayOf(0f, 0f),        // left eye(x, y)
            arrayOf(0f, 0f)         // right eye(x, y)
        )

        var noseLookingAt = arrayOf(0f, 0f)
        var noseYOffset = 0

        var suLookingAt = arrayOf(0f, 0f)

        var distanceFromLeftEye = arrayOf(0f, 0f)
        var distanceFromRightEye = arrayOf(0f, 0f)
        var distanceFromNose = arrayOf(0f, 0f)
        var distanceFromSu = arrayOf(0f, 0f)

        var animator: ValueAnimator = ValueAnimator()

        var lookAtAnimating = false

        var actionShowing = false
        var actionCancelable = false

        var eyesBlinkCount = 0
        var actionDoridoriCount = 0
        var actionDoridoriDirection = 0
        var noseActionCount = 0

        var finishedAction: Byte = 0
        var nextAction = types[(Math.random() * 100).toInt() % types.size]

        var touchFlag = false

        init {

            animator.duration = 350L
            animator.setFloatValues(0f, 1f)
            animator.interpolator = DecelerateInterpolator()
            animator.addListener(object: Animator.AnimatorListener{
                override fun onAnimationStart(p0: Animator?) {}

                override fun onAnimationEnd(p0: Animator?) {
                    lookAtAnimating = false
                }

                override fun onAnimationCancel(p0: Animator?) {}

                override fun onAnimationRepeat(p0: Animator?) {}
            })

            textPaint.typeface = textTypeface
            eyePaint.style = Paint.Style.FILL
            eyePaint.flags = Paint.ANTI_ALIAS_FLAG
            eyeBackPaint.style = Paint.Style.FILL
            eyeBackPaint.flags = Paint.ANTI_ALIAS_FLAG

            brightBack = BitmapFactory.decodeResource(context.resources, R.drawable.kuroneko_back_bright)
            darkBack = BitmapFactory.decodeResource(context.resources, R.drawable.kuroneko_back)

            prefs = PreferenceManager.getDefaultSharedPreferences(context)

            updateSettings()

            handler.post(drawRunner)
            startAction()

        }

        private fun updateSettings(){

            val useBrightMode = prefs.getBoolean(PreferenceKeys.KEY_USE_BRIGHT_MODE, false)
            back = if(useBrightMode){
                brightBack
            }else{
                darkBack
            }

            useText = prefs.getBoolean(PreferenceKeys.KEY_USE_TEXT, false)

            text = prefs.getString(PreferenceKeys.KEY_TEXT, "")!!
            updateTextLayout()

            textPaint.textSize = prefs.getString(PreferenceKeys.KEY_TEXT_SIZE, "40")!!.toFloat()
            textPaint.color = Color.parseColor(prefs.getString(PreferenceKeys.KEY_TEXT_COLOR, "#FFFFFF"))

            bgColor = Color.parseColor(prefs.getString(PreferenceKeys.KEY_BG_COLOR, "#232323"))

            eyeBackPaint.color = Color.parseColor(prefs.getString(PreferenceKeys.KEY_EYES_COLOR, "#e4a600"))

            eyePaint.color = Color.parseColor("#000000")

            eyeCoverPaint.color = if(!useBrightMode) Color.parseColor("#121212") else Color.parseColor("#282828")

            eyebrowPaint.color = Color.parseColor("#000000")

        }

        private fun updateTextLayout(){
            val htmlText = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY)
            textLayoutBuilder = StaticLayout.Builder.obtain(htmlText, 0, htmlText.length, textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
            textLayout = textLayoutBuilder.build()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.visible = visible
            if(visible) {
                updateSettings()
                handler.post(drawRunner)
                startAction()
            } else {
                handler.removeCallbacks(drawRunner)
                handler.removeCallbacks(performActionRunner)
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder?,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height

            val resize = .35

            val backY = (width / 2f - (back.width * resize) / 2f).toInt()
            val backX = (height / 2f - (back.height * resize) / 2f).toInt()

            backRect = Rect(
                backY, backX,
                (backY + back.width * resize).toInt(), (backX + back.height * resize).toInt()
            )

            val noseX = backRect.left + backRect.width() / 2f
            val noseY = backRect.top + backRect.height() * (502f / 617f)
            val noseWidth = nose.width * resize / 2f
            val noseHeight = nose.height * resize / 2f

            noseRect = Rect(
                (noseX - noseWidth).toInt(), (noseY - noseHeight).toInt(),
                (noseX + noseWidth).toInt(), (noseY + noseHeight).toInt()
            )

            val suX = backRect.left + backRect.width() / 2f
            val suY = backRect.top + backRect.height() * (575f / 617f)
            val suWidth = su.width * resize / 2f
            val suHeight = su.height * resize / 2f

            suRect = Rect(
                (suX - suWidth).toInt(), (suY - suHeight).toInt(),
                (suX + suWidth).toInt(), (suY + suHeight).toInt()
            )

            leftEyePosition[0] = backRect.left + (backRect.width() / 2 - backRect.width() * 8.4f / 32f)
            leftEyePosition[1] = backRect.top + backRect.height() / 2f + backRect.height() / 8f

            rightEyePosition[0] = backRect.left + (backRect.width() / 2 + backRect.width() * 8.4f / 32f)
            rightEyePosition[1] = backRect.top + backRect.height() / 2f + backRect.height() / 8f

            nosePosition[0] = noseRect.left + noseRect.width() / 2f
            nosePosition[1] = noseRect.top + noseRect.height() / 2f

            suPosition[0] = suRect.left + suRect.width() / 2f
            suPosition[1] = suRect.top + suRect.height() / 2f

            updateTextLayout()

        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunner)
            handler.removeCallbacks(performActionRunner)
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)

            if(event == null) return

            if(actionShowing && actionCancelable){
                finishAction(false)
            }else if(actionShowing){
                return
            }

            val x = event.rawX.coerceIn(100f, 1340f)
            val y = event.rawY.coerceIn(350f, 2200f)

            when(event.action){
                MotionEvent.ACTION_DOWN -> {
                    touchFlag = true
                    lookAtAnimating = true
                    lookAt(x, y, animate = true)
                }
                MotionEvent.ACTION_MOVE -> {
                    touchFlag = true
                    lookAt(x, y, animate = false, updateValue = !lookAtAnimating)
                }
                MotionEvent.ACTION_UP -> {
                    touchFlag = false
                    lookAtAnimating = true
                    lookAt(-1f, -1f, animate = true)
                }
                MotionEvent.ACTION_CANCEL -> {
                    touchFlag = false
                    lookAtAnimating = true
                    lookAt(-1f, -1f, animate = true)
                }
            }
        }

        private fun draw(){

            val canvas = surfaceHolder.lockCanvas()

            canvas.drawColor(bgColor)

            canvas.drawBitmap(back, null, backRect, null)

            if(eyeType != EYE_TYPE_BLINK) {
                
                canvas.save()
                canvas.translate(eyesBackLookingAt[0][0], eyesBackLookingAt[0][1])
                canvas.drawCircle(leftEyePosition[0], leftEyePosition[1], eyeBackSize, eyeBackPaint)
                canvas.restore()

                canvas.save()
                canvas.translate(eyesLookingAt[0][0], eyesLookingAt[0][1])
                canvas.drawCircle(leftEyePosition[0], leftEyePosition[1], eyeSize, eyePaint)
                canvas.restore()

                canvas.save()
                canvas.translate(eyesBackLookingAt[1][0], eyesBackLookingAt[1][1])
                canvas.drawCircle(
                    rightEyePosition[0],
                    rightEyePosition[1],
                    eyeBackSize,
                    eyeBackPaint
                )
                canvas.restore()

                canvas.save()
                canvas.translate(eyesLookingAt[1][0], eyesLookingAt[1][1])
                canvas.drawCircle(rightEyePosition[0], rightEyePosition[1], eyeSize, eyePaint)
                canvas.restore()
                
                if(eyeType != EYE_TYPE_NORMAL) {
                    when (eyeType) {
                        EYE_TYPE_HALF -> {
                            drawEyeCover(canvas)
                            drawEyebrow(canvas, 1)
                        }
                        EYE_TYPE_ANGRY -> {
                            drawEyeCover(canvas, true)
                            drawEyebrow(canvas, 2)
                        }
                    }
                } else {
                    drawEyebrow(canvas, 0)
                }
                
            } else {
                canvas.save()
                canvas.translate(eyesLookingAt[0][0], eyesLookingAt[0][1])
                canvas.drawRect(
                    leftEyePosition[0] - eyeBackSize, leftEyePosition[1] - 12,
                    leftEyePosition[0] + eyeBackSize, leftEyePosition[1] + 12,
                    eyeBackPaint
                )
                canvas.restore()

                canvas.save()
                canvas.translate(eyesLookingAt[1][0], eyesLookingAt[1][1])
                canvas.drawRect(
                    rightEyePosition[0] - eyeBackSize, rightEyePosition[1] - 12,
                    rightEyePosition[0] + eyeBackSize, rightEyePosition[1] + 12,
                    eyeBackPaint
                )
                canvas.restore()

                drawEyebrow(canvas, 0)
            }

            canvas.save()
            canvas.translate(noseLookingAt[0], noseLookingAt[1] + noseYOffset)
            canvas.drawBitmap(nose, null, noseRect, null)
            canvas.restore()

            canvas.save()
            canvas.translate(suLookingAt[0], suLookingAt[1] + noseYOffset)
            canvas.drawBitmap(su, null, suRect, null)
            canvas.restore()

            if(useText) {
                canvas.save()
                canvas.translate(0f, height * 5f / 7f)
                textLayout.draw(canvas)
                canvas.restore()
            }

            surfaceHolder.unlockCanvasAndPost(canvas)

            handler.removeCallbacks(drawRunner)
            if(visible) handler.postDelayed(drawRunner, 10L)

        }

        private fun drawEyeCover(canvas: Canvas, rotate: Boolean = false){
            canvas.save()
            canvas.translate(eyesLookingAt[0][0], eyesLookingAt[0][1])
            if(rotate) {
                canvas.translate(4f, 0f)
                canvas.rotate(15f, leftEyePosition[0], leftEyePosition[1] - eyeBackSize + 35f)
            }
            canvas.drawRect(
                leftEyePosition[0] - eyeBackSize, leftEyePosition[1] - eyeBackSize - 10f,
                leftEyePosition[0] + eyeBackSize, leftEyePosition[1] - eyeBackSize + 35f,
                eyeCoverPaint
            )
            canvas.restore()

            canvas.save()
            canvas.translate(eyesLookingAt[1][0], eyesLookingAt[1][1])
            if(rotate) {
                canvas.translate(-4f, 0f)
                canvas.rotate(-15f, rightEyePosition[0], rightEyePosition[1] - eyeBackSize + 35f)
            }
            canvas.drawRect(
                rightEyePosition[0] - eyeBackSize, rightEyePosition[1] - eyeBackSize - 10f,
                rightEyePosition[0] + eyeBackSize, rightEyePosition[1] - eyeBackSize + 35f,
                eyeCoverPaint
            )
            canvas.restore()
        }
        
        private fun drawEyebrow(canvas: Canvas, type: Byte){
            
            val eyebrowOffset = when(type){
                0.toByte() -> 75f
                1.toByte() -> 30f
                2.toByte() -> 40f
                else -> 0f
            }
            
            canvas.save()
            canvas.translate(eyesLookingAt[0][0], eyesLookingAt[0][1])
            if(type == 2.toByte()){
                canvas.translate(4f, 0f)
                canvas.rotate(15f, leftEyePosition[0], leftEyePosition[1] - eyeBackSize + eyebrowHeight)
            }
            canvas.drawRect(
                leftEyePosition[0] - eyeBackSize, leftEyePosition[1] - eyeBackSize - eyebrowOffset,
                leftEyePosition[0] + eyeBackSize, leftEyePosition[1] - eyeBackSize - eyebrowOffset + eyebrowHeight,
                eyebrowPaint
            )
            canvas.restore()

            canvas.save()
            canvas.translate(eyesLookingAt[1][0], eyesLookingAt[1][1])
            if(type == 2.toByte()){
                canvas.translate(-4f, 0f)
                canvas.rotate(-15f, rightEyePosition[0], rightEyePosition[1] - eyeBackSize + eyebrowHeight)
            }
            canvas.drawRect(
                rightEyePosition[0] - eyeBackSize, rightEyePosition[1] - eyeBackSize - eyebrowOffset,
                rightEyePosition[0] + eyeBackSize, rightEyePosition[1] - eyeBackSize - eyebrowOffset + eyebrowHeight,
                eyebrowPaint
            )
            canvas.restore()

        }

        private fun lookAt(x: Float, y: Float, animate: Boolean, updateValue: Boolean = false){

            distanceFromLeftEye[0] = if(x < 0 || y < 0) 0f else x - leftEyePosition[0]
            distanceFromLeftEye[1] = if(x < 0 || y < 0) 0f else y - leftEyePosition[1]

            distanceFromRightEye[0] = if(x < 0 || y < 0) 0f else x - rightEyePosition[0]
            distanceFromRightEye[1] = if(x < 0 || y < 0) 0f else y - rightEyePosition[1]

            distanceFromNose[0] = if(x < 0 || y < 0) 0f else x - nosePosition[0]
            distanceFromNose[1] = if(x < 0 || y < 0) 0f else y - nosePosition[1]

            distanceFromSu[0] = if(x < 0 || y < 0) 0f else x - suPosition[0]
            distanceFromSu[1] = if(x < 0 || y < 0) 0f else y - suPosition[1]
            
            if(animate){
                animator.removeAllUpdateListeners()
                animator.addUpdateListener {
                    setLookAtValues(it)
                }
                animator.start()
            }else if(updateValue){
                setLookAtValues(null)
            }

        }

        private fun setLookAtValues(animator: ValueAnimator?) {
            val animatedValue = if(animator == null) 1f else animator.animatedValue as Float

            eyesBackLookingAt[0][0] =
                eyesBackLookingAt[0][0] + (distanceFromLeftEye[0] / 20f - eyesBackLookingAt[0][0]) * animatedValue
            eyesBackLookingAt[0][1] =
                eyesBackLookingAt[0][1] + (distanceFromLeftEye[1] / 20f - eyesBackLookingAt[0][1]) * animatedValue

            eyesBackLookingAt[1][0] =
                eyesBackLookingAt[1][0] + (distanceFromRightEye[0] / 20f - eyesBackLookingAt[1][0]) * animatedValue
            eyesBackLookingAt[1][1] =
                eyesBackLookingAt[1][1] + (distanceFromRightEye[1] / 20f - eyesBackLookingAt[1][1]) * animatedValue

            eyesLookingAt[0][0] =
                eyesLookingAt[0][0] + (distanceFromLeftEye[0] / 16.7f - eyesLookingAt[0][0]) * animatedValue
            eyesLookingAt[0][1] =
                eyesLookingAt[0][1] + (distanceFromLeftEye[1] / 16.7f - eyesLookingAt[0][1]) * animatedValue

            eyesLookingAt[1][0] =
                eyesLookingAt[1][0] + (distanceFromRightEye[0] / 16.7f - eyesLookingAt[1][0]) * animatedValue
            eyesLookingAt[1][1] =
                eyesLookingAt[1][1] + (distanceFromRightEye[1] / 16.7f - eyesLookingAt[1][1]) * animatedValue

            noseLookingAt[0] =
                noseLookingAt[0] + (distanceFromNose[0] / 20f - noseLookingAt[0]) * animatedValue
            noseLookingAt[1] =
                noseLookingAt[1] + (distanceFromNose[1] / 20f - noseLookingAt[1]) * animatedValue

            suLookingAt[0] =
                suLookingAt[0] + (distanceFromSu[0] / 24.5f - suLookingAt[0]) * animatedValue
            suLookingAt[1] =
                suLookingAt[1] + (distanceFromSu[1] / 20f - suLookingAt[1]) * animatedValue

        }

        private fun startAction(){
            if(finishedAction == ACTION_BLINK && nextAction == ACTION_BLINK){
                handler.postDelayed(
                    performActionRunner,
                    150L + 150L * (Math.random()*5).toLong()
                )
            }else {
                handler.postDelayed(
                    performActionRunner,
                    delays[(Math.random() * 100).toInt() % delays.size].toLong()
                )
            }
        }

        private fun performAction(type: Byte){
            if(actionShowing) return

            if(touchFlag) {
                handler.removeCallbacks(performActionRunner)
                startAction()
                return
            }

            actionShowing = true
            actionCancelable = false

            when(type){
                ACTION_LOOK_AT -> {
                    actionCancelable = true
                    lookAtAnimating = true
                    lookAt(100f + Math.random().toFloat() * 1240f, 350f + Math.random().toFloat() * 1850f, animate = true)

                    handler.postDelayed({
                        finishAction(true)
                    }, 2000L + 500L * ((Math.random() * 100).toInt() % 8 + 1))
                }
                ACTION_HALF_EYES -> {
                    eyeType = EYE_TYPE_BLINK

                    handler.postDelayed({
                        eyeType = EYE_TYPE_HALF
                    }, 150L)
                }
                ACTION_ANGRY -> {
                    eyeType = EYE_TYPE_BLINK

                    handler.postDelayed({
                        eyeType = EYE_TYPE_ANGRY
                    }, 150L)
                }
                ACTION_BLINK -> {
                    eyeType = EYE_TYPE_BLINK

                    handler.postDelayed({
                        finishAction(false)
                    }, 150L)
                }
                ACTION_DORIDORI -> {
                    animator.duration = 250L
                    actionDoridoriDirection = (Math.random() * 10).toInt() % 2
                    doridori()
                }
                ACTION_SMELLING -> {
                    smelling()
                }
            }

            if(type == ACTION_HALF_EYES || type == ACTION_ANGRY) {
                handler.postDelayed(
                    {
                        eyeType = EYE_TYPE_BLINK
                        handler.postDelayed(finishActionRunner, 150L)
                    },
                    2150L + 500L * ((Math.random() * 100).toInt() % 8 + 1)
                )
            }

            finishedAction = type

        }

        private fun finishAction(lookAt: Boolean = false){
            eyeType = EYE_TYPE_NORMAL

            animator.duration = 350L

            if(lookAt){
                lookAtAnimating = true
                lookAt(-1f, -1f, animate = true)
            }

            actionShowing = false

            nextAction = types[(Math.random() * 100).toInt() % types.size]
            eyesBlinkCount = if(nextAction != 3.toByte()) 0 else eyesBlinkCount+1
            if(eyesBlinkCount > 3){
                nextAction = types[(Math.random() * 100).toInt() % (types.size - 18)]
            }

            handler.removeCallbacks(performActionRunner)
            startAction()
        }

        private fun doridori(){

            actionDoridoriCount++
            if(actionDoridoriCount <= 4){
                lookAt(if(actionDoridoriCount % 2 == actionDoridoriDirection) width.toFloat() else 0f, height / 2f, animate = true)
                handler.postDelayed({ doridori() }, animator.duration)
            }else{
                actionDoridoriCount = 0
                finishAction(true)
            }

        }

        private fun smelling(){

            noseActionCount++
            if(noseActionCount <= 4){
                noseYOffset = if(noseActionCount % 2 == 1) -12 else 0
                handler.postDelayed({ smelling() }, 200L)
            }else{
                noseActionCount = 0
                finishAction(false)
            }

        }

    }

}