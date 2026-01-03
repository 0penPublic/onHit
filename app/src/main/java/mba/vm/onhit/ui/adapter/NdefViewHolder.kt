package mba.vm.onhit.ui.adapter

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mba.vm.onhit.R

class NdefViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val title: TextView = view.findViewById(R.id.title)
    val subtitle: TextView = view.findViewById(R.id.subtitle)
}