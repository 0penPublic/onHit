package mba.vm.onhit.ui.decorator

import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpacingItemDecoration(
    private val horizontalDp: Int,
    private val verticalDp: Int
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val displayMetrics = view.resources.displayMetrics
        val hPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            horizontalDp.toFloat(),
            displayMetrics
        ).toInt()
        val vPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            verticalDp.toFloat(),
            displayMetrics
        ).toInt()

        outRect.left = hPx
        outRect.right = hPx
        outRect.top = vPx
        outRect.bottom = vPx
    }
}