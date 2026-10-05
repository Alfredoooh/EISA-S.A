package com.appao

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class CommentAdapter(
    private val list: MutableList<Comment>,
    private val onClickReact: (Int, String) -> Unit
) : RecyclerView.Adapter<CommentAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: ImageView = v.findViewById(R.id.cmAvatar)
        val user: TextView = v.findViewById(R.id.cmUser)
        val time: TextView = v.findViewById(R.id.cmTime)
        val text: TextView = v.findViewById(R.id.cmText)
        val reactsRow: LinearLayout = v.findViewById(R.id.cmReactsRow)
        val picker: LinearLayout = v.findViewById(R.id.cmPicker)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val c = list[position]

        h.user.text = c.user
        h.time.text = c.date
        h.text.text = c.text

        if (c.avatar.isNotEmpty()) {
            com.bumptech.glide.Glide.with(h.avatar)
                .load(c.avatar)
                .dontAnimate()
                .into(h.avatar)
        } else {
            h.avatar.setImageDrawable(
                ContextCompat.getDrawable(
                    h.itemView.context,
                    R.drawable.ic_avatar_default
                )
            )
        }

        bindReactionCounts(h, c)

        h.picker.removeAllViews()
        val reacts = listOf(
            "like" to "Gosto",
            "love" to "Adoro",
            "haha" to "Riso",
            "wow" to "Uau",
            "sad" to "Triste",
            "angry" to "Raiva"
        )

        reacts.forEach { (kind, label) ->
            val iv = ImageView(h.itemView.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dp(h.itemView, 38),
                    dp(h.itemView, 38)
                ).apply {
                    marginEnd = dp(h.itemView, 4)
                }
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = label
                alpha = 1f
                scaleX = 1f
                scaleY = 1f
                setPadding(
                    dp(h.itemView, 2),
                    dp(h.itemView, 2),
                    dp(h.itemView, 2),
                    dp(h.itemView, 2)
                )
            }

            IconLoader.applyPng(iv, kind)

            iv.setOnClickListener {
                val adapterPosition = h.bindingAdapterPosition
                if (adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener

                iv.animate()
                    .scaleX(1.18f)
                    .scaleY(1.18f)
                    .setDuration(90L)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        iv.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(110L)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                        onClickReact(adapterPosition, kind)
                    }
                    .start()
            }

            h.picker.addView(iv)
        }

        h.picker.visibility = View.GONE
        h.itemView.setOnClickListener {
            val show = h.picker.visibility != View.VISIBLE
            if (show) {
                h.picker.visibility = View.VISIBLE
                h.picker.bringToFront()
                h.itemView.requestLayout()
                h.itemView.post { h.itemView.parent?.requestLayout() }
                animatePickerIn(h.picker)
            } else {
                h.picker.animate().cancel()
                h.picker.animate()
                    .alpha(0f)
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(120L)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        h.picker.visibility = View.GONE
                        h.picker.alpha = 1f
                        h.picker.scaleX = 1f
                        h.picker.scaleY = 1f
                    }
                    .start()
            }
        }
    }

    private fun bindReactionCounts(
        h: VH,
        c: Comment
    ) {
        h.reactsRow.removeAllViews()

        val shown = c.reactions.filterValues { it > 0 }
        if (shown.isEmpty()) {
            h.reactsRow.visibility = View.GONE
            return
        }

        h.reactsRow.visibility = View.VISIBLE

        for ((kind, count) in shown) {
            val chip = LinearLayout(h.itemView.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(
                    dp(h.itemView, 7),
                    dp(h.itemView, 3),
                    dp(h.itemView, 7),
                    dp(h.itemView, 3)
                )
                background = roundedChip(h)
            }

            val icon = ImageView(h.itemView.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dp(h.itemView, 18),
                    dp(h.itemView, 18)
                )
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            IconLoader.applyPng(icon, kind)

            val countView = TextView(h.itemView.context).apply {
                text = count.toString()
                textSize = 11.5f
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.dim
                    )
                )
                setPadding(
                    dp(h.itemView, 4),
                    0,
                    0,
                    0
                )
            }

            chip.addView(icon)
            chip.addView(countView)

            h.reactsRow.addView(
                chip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(h.itemView, 6)
                }
            )
        }
    }

    private fun animatePickerIn(picker: LinearLayout) {
        picker.alpha = 0f
        picker.scaleX = 0.92f
        picker.scaleY = 0.92f

        picker.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220L)
            .setInterpolator(OvershootInterpolator(0.55f))
            .start()

        for (i in 0 until picker.childCount) {
            val child = picker.getChildAt(i)
            child.alpha = 0f
            child.scaleX = 0.45f
            child.scaleY = 0.45f
            child.translationY = dp(child, 7)

            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(child, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(child, View.SCALE_X, 0.45f, 1f),
                    ObjectAnimator.ofFloat(child, View.SCALE_Y, 0.45f, 1f),
                    ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, dp(child, 7).toFloat(), 0f)
                )
                duration = 220L
                startDelay = 22L * i
                interpolator = OvershootInterpolator(0.75f)
                start()
            }
        }
    }

    private fun roundedChip(
        h: VH
    ): android.graphics.drawable.Drawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(h.itemView, 14).toFloat()
            setColor(
                ContextCompat.getColor(
                    h.itemView.context,
                    R.color.bgElevated
                )
            )
            setStroke(
                dp(h.itemView, 1),
                ContextCompat.getColor(
                    h.itemView.context,
                    R.color.border
                )
            )
        }

    override fun getItemCount(): Int = list.size

    private fun dp(v: View, value: Int): Int =
        (value * v.resources.displayMetrics.density).toInt()
}
