@SuppressLint("ClickableViewAccessibility")
class ZImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {
    var _mode: Int = NONE_MODE

    var _matrix: Matrix = Matrix()
    var _previousPoint: PointF = PointF()
    var _startPoint: PointF = PointF()
    var _currentScale: Float = 1f
    var _minScale: Float = 1f
    var _maxScale: Float = 3f
    var _arrayOf9Floats: FloatArray
    var _bitmapWidth: Float = 0f
    var _bitmapHeight: Float = 0f
    var _displayWidth: Float = 0f
    var _displayHeight: Float = 0f
    var _scaleDetector: ScaleGestureDetector
    var _context: Context

    init {
        super.setClickable(true)
        _context = context
        _scaleDetector = ScaleGestureDetector(context, ScaleListener())
        _arrayOf9Floats = FloatArray(9)
        scaleType = ScaleType.MATRIX
        setOnTouchListener { v, event -> handleTouch(v, event) }
    }

    private fun handleTouch(v: View, event: MotionEvent): Boolean {
        _scaleDetector.onTouchEvent(event)
        //Contrary to how this line looks, the matrix is not setting the values from the arrayOf9Floats, but rather taking the
        //matrix values and assigning them into the arrayOf9Floats. I extremely dislike this syntax and I think
        //it should have been written as _arrayOf9Floats = _matrix.getValues() but that's Android for you!!!
        _matrix.getValues(_arrayOf9Floats)

        //Look at https://youtu.be/IiXB6tYtY4w?t=4m12s , it shows scale, rotate, and translate matrices
        //If you look at the translate matrix, you'll see that the 3rd and 6th values are the values which represent x and y translations respectively
        //this corresponds to the 2nd and 5th values in the array and hence why the MTRANS_X and MTRANS_Y have the constants 2 and 5 respectively
        val xTranslate = _arrayOf9Floats[Matrix.MTRANS_X]
        val yTranslate = _arrayOf9Floats[Matrix.MTRANS_Y]
        val currentEventPoint = PointF(event.x, event.y)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                _previousPoint[event.x] = event.y
                _startPoint.set(_previousPoint)
                _mode = DRAG_MODE
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                _previousPoint[event.x] = event.y
                _startPoint.set(_previousPoint)
                _mode = ZOOM_MODE
            }

            MotionEvent.ACTION_MOVE -> if (_mode == ZOOM_MODE || _mode == DRAG_MODE) {
                var deltaX = currentEventPoint.x - _previousPoint.x
                var deltaY = currentEventPoint.y - _previousPoint.y
                //In matrix terms, going right is + and going left is +
                //Moving the image right past 0 means it will show alpha space on the left so we dont want that
                //Keep in mind this is a TOP LEFT pivot point, so we dont want the top left to be past 0 lest we have alpha space
                if (xTranslate + deltaX > 0) {
                    //get absolute of how much into the negative we would have gone
                    val excessDeltaX =
                        abs((xTranslate + deltaX).toDouble()).toFloat()
                    //take that excess away from deltaX so X wont got less than 0 after the translation
                    deltaX -= excessDeltaX
                }

                //Going left we dont want the negative value to be less than the negative width of the sprite, lest we get alpha space on the right
                //The width is going to be the width of the bitmap * scale and we want the - of it because we are checking for left movement
                //We also need to account for the width of the DISPLAY CONTAINER (i.e. _displayWidth) so that gets subtracted
                //i.e. we want the max scroll width value
                val maxScrollableWidth = _bitmapWidth * _currentScale - _displayWidth
                if (xTranslate + deltaX < -maxScrollableWidth) {
                    //this forces the max possible translate to always match the - of maxScrollableWidth
                    deltaX = -maxScrollableWidth - xTranslate
                }

                //repeat for Y
                if (yTranslate + deltaY > 0) {
                    val excessDeltaY =
                        abs((yTranslate + deltaY).toDouble()).toFloat()
                    deltaY -= excessDeltaY
                }

                val maxScrollableHeight = _bitmapHeight * _currentScale - _displayWidth
                if (yTranslate + deltaY < -maxScrollableHeight) {
                    //this forces the max possible translate to always match the - of maxScrollableWidth
                    deltaY = -maxScrollableHeight - yTranslate
                }

                _matrix.postTranslate(deltaX, deltaY)
                _matrix.getValues(_arrayOf9Floats)

                //System.out.println(_matrix);
                _previousPoint[currentEventPoint.x] = currentEventPoint.y
            }

            MotionEvent.ACTION_POINTER_UP -> _mode = NONE_MODE
        }
        imageMatrix = _matrix
        invalidate()
        return true
    }

    override fun setImageBitmap(bm: Bitmap) {
        super.setImageBitmap(bm)
        _bitmapWidth = bm.width.toFloat()
        _bitmapHeight = bm.height.toFloat()
        invalidate()
    }

    override fun setImageResource(resid: Int) {
        val bitmapImage = BitmapFactory.decodeResource(_context.resources, resid)
        setImageBitmap(bitmapImage)
    }

    private inner class ScaleListener : SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            _mode = ZOOM_MODE
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var scaleFactor = detector.scaleFactor
            val originalScale = _currentScale
            _currentScale *= scaleFactor
            //Zoom in too much
            if (_currentScale > _maxScale) {
                _currentScale = _maxScale
                scaleFactor = _maxScale / originalScale
            } //Zoom out too much
            else if (_currentScale < _minScale) {
                _currentScale = _minScale
                scaleFactor = _minScale / originalScale
            }
            _matrix.postScale(scaleFactor, scaleFactor)

            return true
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        _displayWidth = MeasureSpec.getSize(widthMeasureSpec).toFloat()
        _displayHeight = MeasureSpec.getSize(heightMeasureSpec).toFloat()
        adjustScale()
    }

    private fun adjustScale() {
        //Fit to display bounds with NO alpha space
        val scale: Float
        val scaleX = _displayWidth / _bitmapWidth
        val scaleY = _displayHeight / _bitmapHeight
        scale = max(scaleX.toDouble(), scaleY.toDouble()).toFloat()
        _matrix.setScale(scale, scale)
        imageMatrix = _matrix
        _currentScale = scale
        _minScale = scale
    }

    fun setMaxZoom(maxZoom: Float) {
        _maxScale = maxZoom
    }

    fun setMinZoom(minZoom: Float) {
        _minScale = minZoom
    }

    companion object {
        const val NONE_MODE: Int = 0
        const val DRAG_MODE: Int = 1
        const val ZOOM_MODE: Int = 2
    }
}
