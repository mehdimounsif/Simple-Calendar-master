package com.simplemobiletools.calendar.fragments

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.simplemobiletools.calendar.R
import com.simplemobiletools.calendar.activities.MainActivity
import com.simplemobiletools.calendar.adapters.MyDayPagerAdapter
import com.simplemobiletools.calendar.extensions.config
import com.simplemobiletools.calendar.helpers.DAY_CODE
import com.simplemobiletools.calendar.helpers.Formatter
import com.simplemobiletools.calendar.interfaces.WeekFragmentListener
import com.simplemobiletools.calendar.views.MyScrollView
import com.simplemobiletools.commons.views.MyViewPager
import kotlinx.android.synthetic.main.fragment_days_holder.*
import kotlinx.android.synthetic.main.fragment_days_holder.view.*
import org.joda.time.DateTime
import java.util.*

class DayFragmentsHolder : MyFragmentHolder(), WeekFragmentListener {

    private val PREFILLED_DAYS = 251
    private var weekHolder: ViewGroup? = null

    private var viewPager: MyViewPager? = null
    private var defaultDailyPage = 0
    private var todayDayCode = ""
    private var currentDayCode = ""
    private var isGoToTodayVisible = false
    private var weekScrollY = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentDayCode = arguments?.getString(DAY_CODE) ?: ""
        todayDayCode = Formatter.getTodayCode(context!!)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        weekHolder = inflater.inflate(R.layout.fragment_days_holder, container, false) as ViewGroup
        weekHolder!!.background = ColorDrawable(context!!.config.backgroundColor)
        viewPager = weekHolder!!.fragment_days_viewpager
        viewPager!!.id = (System.currentTimeMillis() % 100000).toInt()
        setupFragment()
        return weekHolder
    }

    private fun setupFragment() {
        val codes = getDays(currentDayCode)
        val dailyAdapter = MyDayPagerAdapter(activity!!.supportFragmentManager, codes, this)
        defaultDailyPage = codes.size / 2

        val textColor = context!!.config.textColor
        weekHolder!!.week_view_hours_holder.removeAllViews()
        val hourDateTime = DateTime().withDate(2000, 1, 1).withTime(0, 0, 0, 0)
        for (i in 1..23) {
            val formattedHours = Formatter.getHours(context!!, hourDateTime.withHourOfDay(i))
            (layoutInflater.inflate(R.layout.weekly_view_hour_textview, null, false) as TextView).apply {
                text = formattedHours
                setTextColor(textColor)
                weekHolder!!.week_view_hours_holder.addView(this)
            }
        }

        viewPager!!.apply {
            adapter = dailyAdapter
            addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrollStateChanged(state: Int) {
                }

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                }

                override fun onPageSelected(position: Int) {
                    currentDayCode = codes[position]
                    val shouldGoToTodayBeVisible = shouldGoToTodayBeVisible()
                    if (isGoToTodayVisible != shouldGoToTodayBeVisible) {
                        (activity as? MainActivity)?.toggleGoToTodayVisibility(shouldGoToTodayBeVisible)
                        isGoToTodayVisible = shouldGoToTodayBeVisible
                    }
                }
            })
            currentItem = defaultDailyPage
        }

        weekHolder!!.week_view_hours_scrollview.setOnScrollviewListener(object : MyScrollView.ScrollViewListener {
            override fun onScrollChanged(scrollView: MyScrollView, x: Int, y: Int, oldx: Int, oldy: Int) {
                weekScrollY = y
                dailyAdapter.updateScrollY(fragment_days_viewpager.currentItem, y)
            }
        })
        weekHolder!!.week_view_hours_scrollview.setOnTouchListener { view, motionEvent -> true }
        updateActionBarTitle()
    }

    private fun getDays(code: String): List<String> {
        val days = ArrayList<String>(PREFILLED_DAYS)
        val today = Formatter.getDateTimeFromCode(code)
        for (i in -PREFILLED_DAYS / 2..PREFILLED_DAYS / 2) {
            days.add(Formatter.getDayCodeFromDateTime(today.plusDays(i)))
        }
        return days
    }

//    override fun goLeft() {
//        viewPager!!.currentItem = viewPager!!.currentItem - 1
//    }
//
//    override fun goRight() {
//        viewPager!!.currentItem = viewPager!!.currentItem + 1
//    }
//
//    override fun goToDateTime(dateTime: DateTime) {
//        currentDayCode = Formatter.getDayCodeFromDateTime(dateTime)
//        setupFragment()
//    }

    override fun scrollTo(y: Int) {
        weekHolder!!.week_view_hours_scrollview.scrollY = y
        weekScrollY = y
    }

    override fun updateHoursTopMargin(margin: Int) {
        weekHolder?.week_view_hours_divider?.layoutParams?.height = margin
        weekHolder?.week_view_hours_scrollview?.requestLayout()
    }

    override fun getCurrScrollY() = weekScrollY


    override fun goToToday() {
        currentDayCode = todayDayCode
        setupFragment()
    }

    override fun refreshEvents() {
        (viewPager?.adapter as? MyDayPagerAdapter)?.updateCalendars(viewPager?.currentItem ?: 0)
    }

    override fun shouldGoToTodayBeVisible() = currentDayCode != todayDayCode

    override fun updateActionBarTitle() {
        (activity as MainActivity).supportActionBar?.title = getString(R.string.app_launcher_name)
    }

    override fun getNewEventDayCode() = currentDayCode
}
