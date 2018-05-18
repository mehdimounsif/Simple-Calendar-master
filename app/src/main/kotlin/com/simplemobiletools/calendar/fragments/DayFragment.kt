package com.simplemobiletools.calendar.fragments

import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.simplemobiletools.calendar.R
import com.simplemobiletools.calendar.activities.EventActivity
import com.simplemobiletools.calendar.activities.SimpleActivity
import com.simplemobiletools.calendar.adapters.DayEventsAdapter
import com.simplemobiletools.calendar.extensions.config
import com.simplemobiletools.calendar.extensions.dbHelper
import com.simplemobiletools.calendar.extensions.getFilteredEvents
import com.simplemobiletools.calendar.helpers.*
import com.simplemobiletools.calendar.helpers.Formatter
import com.simplemobiletools.calendar.interfaces.NavigationListener
import com.simplemobiletools.calendar.interfaces.WeekFragmentListener
import com.simplemobiletools.calendar.models.Event
import com.simplemobiletools.calendar.views.MyScrollView
import com.simplemobiletools.commons.extensions.*
import kotlinx.android.synthetic.main.fragment_day.view.*
import kotlinx.android.synthetic.main.top_navigation.view.*
import org.joda.time.DateTime
import java.util.*

class DayFragment : Fragment() {
    //var mListener: NavigationListener? = null
    var mListener: WeekFragmentListener? = null
    private var mTextColor = 0
    private var mDayCode = ""
    private var lastHash = 0
    private var eventTypeColors = SparseIntArray()
    lateinit var mView: View
    private var minScrollY = -1
    private var maxScrollY = -1
    lateinit var mHolder: RelativeLayout
    lateinit var mScrollView: MyScrollView
    private var wasFragmentInit = false
    private var isFragmentVisible = false
    lateinit var mRes: Resources
    private var mRowHeight = 0
    private var primaryColor = 0
    lateinit var inflater: LayoutInflater
    private var dimPastEvents = true
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.inflater = inflater
        mView = inflater.inflate(R.layout.fragment_day, container, false)
        mHolder = mView.day_holder
        mScrollView = mView.day_events_scrollview
        mScrollView.setOnScrollviewListener(object : MyScrollView.ScrollViewListener {
            override fun onScrollChanged(scrollView: MyScrollView, x: Int, y: Int, oldx: Int, oldy: Int) {
                checkScrollLimits(y)
            }
        })
        mScrollView.onGlobalLayout {
            updateScrollY(Math.max(mListener?.getCurrScrollY() ?: 0, minScrollY))
        }
        (0..23).map {
            inflater.inflate(R.layout.stroke_horizontal_divider, mView.day_horizontal_grid_holder)
        }
        mDayCode = arguments!!.getString(DAY_CODE)
        wasFragmentInit = true
        setupButtons()
        return mView
    }

    private fun checkScrollLimits(y: Int) {
        if (minScrollY != -1 && y < minScrollY) {
            mScrollView.scrollY = minScrollY
        } else if (maxScrollY != -1 && y > maxScrollY) {
            mScrollView.scrollY = maxScrollY
        } else if (isFragmentVisible) {
            mListener?.scrollTo(y)
        }
    }

    override fun onResume() {
        super.onResume()
        updateCalendar()
    }

    private fun setupButtons() {
        mTextColor = context!!.config.textColor

        mHolder.top_left_arrow.apply {
            applyColorFilter(mTextColor)
            background = null
            setOnClickListener {
                //mListener?.goLeft()
            }
        }

        mHolder.top_right_arrow.apply {
            applyColorFilter(mTextColor)
            background = null
            setOnClickListener {
                // mListener?.goRight()
            }
        }

        val day = Formatter.getDayTitle(context!!, mDayCode)
        mHolder.top_value.apply {
            text = day
            setOnClickListener { pickDay() }
            setTextColor(context.config.textColor)
        }
    }

    private fun pickDay() {
        activity!!.setTheme(context!!.getDialogTheme())
        val view = layoutInflater.inflate(R.layout.date_picker, null)
        val datePicker = view.findViewById<DatePicker>(R.id.date_picker)

        val dateTime = Formatter.getDateTimeFromCode(mDayCode)
        datePicker.init(dateTime.year, dateTime.monthOfYear - 1, dateTime.dayOfMonth, null)

        AlertDialog.Builder(context!!)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { dialog, which -> positivePressed(dateTime, datePicker) }
                .create().apply {
                    activity?.setupDialogStuff(view, this)
                }
    }

    private fun positivePressed(dateTime: DateTime, datePicker: DatePicker) {
        val month = datePicker.month + 1
        val year = datePicker.year
        val day = datePicker.dayOfMonth
        val newDateTime = dateTime.withDate(year, month, day)
        // mListener?.goToDateTime(newDateTime)
    }

    fun updateCalendar() {
        val startTS = Formatter.getDayStartTS(mDayCode)
        val endTS = Formatter.getDayEndTS(mDayCode)
        mRowHeight = (context!!.resources.getDimension(R.dimen.weekly_view_row_height)).toInt()
        minScrollY = mRowHeight * context!!.config.startWeeklyAt
        dimPastEvents = context!!.config.dimPastEvents
        primaryColor = context!!.getAdjustedPrimaryColor()
        mRes = resources

        context!!.dbHelper.getEvents(startTS, endTS) {
            receivedEvents(it)
        }
    }

    private fun receivedEvents(events: List<Event>) {
        val filtered = context?.getFilteredEvents(events) ?: ArrayList()
        val newHash = filtered.hashCode()
        if (newHash == lastHash || !isAdded) {
            return
        }
        lastHash = newHash

        val replaceDescription = context!!.config.replaceDescription
        val sorted = ArrayList<Event>(filtered.sortedWith(compareBy({ !it.getIsAllDay() }, { it.startTS }, { it.endTS }, { it.title }, {
            if (replaceDescription) it.location else it.description
        })))

        activity?.runOnUiThread {
            updateEvents(sorted)
        }
    }

    private fun updateEvents(events: ArrayList<Event>) {
        if (activity == null)
            return
        val fullHeight = mRes.getDimension(R.dimen.weekly_view_events_height)
        val minuteHeight = fullHeight / (24 * 60)
        val minimalHeight = mRes.getDimension(R.dimen.weekly_view_minimal_event_height).toInt()
        context!!.dbHelper.getEventTypes {
            it.map { eventTypeColors.put(it.id, it.color) }
        }
        mView.day_events_columns_holder.removeAllViews()
        for (event in events) {
            val startDateTime = Formatter.getDateTimeFromTS(event.startTS)
            val endDateTime = Formatter.getDateTimeFromTS(event.endTS)
            val dayOfWeek = startDateTime.plusDays(if (context!!.config.isSundayFirst) 1 else 0).dayOfWeek - 1
            val startMinutes = startDateTime.minuteOfDay
            val duration = endDateTime.minuteOfDay - startMinutes
            (inflater.inflate(R.layout.week_event_marker, null, false) as TextView).apply {
                var backgroundColor = eventTypeColors.get(event.eventType, primaryColor)
                var textColor = backgroundColor.getContrastColor()
                if (dimPastEvents && event.isPastEvent) {
                    backgroundColor = backgroundColor.adjustAlpha(LOW_ALPHA)
                    textColor = textColor.adjustAlpha(LOW_ALPHA)
                }

                background = ColorDrawable(backgroundColor)
                setTextColor(textColor)
                text = event.title
                mView.day_events_columns_holder.addView(this)
                y = startMinutes * minuteHeight
                (layoutParams as LinearLayout.LayoutParams).apply {
                    width = mView.day_events_columns_holder.width - 1
                    minHeight = if (event.startTS == event.endTS) minimalHeight else (duration * minuteHeight).toInt() - 1
                }
                setOnClickListener {
                    Intent(context, EventActivity::class.java).apply {
                        putExtra(EVENT_ID, event.id)
                        putExtra(EVENT_OCCURRENCE_TS, event.startTS)
                        startActivity(this)
                    }
                }
            }
        }
//        DayEventsAdapter(activity as SimpleActivity, events, mHolder.day_events) {
//            editEvent(it as Event)
//        }.apply {
//            addVerticalDividers(true)
//            mHolder.day_events.adapter = this
//        }
    }

    private fun editEvent(event: Event) {
        Intent(context, EventActivity::class.java).apply {
            putExtra(EVENT_ID, event.id)
            putExtra(EVENT_OCCURRENCE_TS, event.startTS)
            startActivity(this)
        }
    }

    fun updateScrollY(y: Int) {
        if (wasFragmentInit) {
            mScrollView.scrollY = y
        }
    }
}
