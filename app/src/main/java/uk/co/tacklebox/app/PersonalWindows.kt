/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import uk.co.tacklebox.app.data.CatchRow
import uk.co.tacklebox.app.services.Astronomy
import java.time.*
import kotlin.math.roundToInt

data class PersonalWindowSummary(val matches:Int,val total:Int,val hourBand:Int) {
    val percent:Int get()=if(total==0)0 else (100.0*matches/total).roundToInt()
}
object PersonalWindows {
    fun summary(dates:List<Instant>,lat:Double,lon:Double,zone:ZoneId):PersonalWindowSummary {
        val matches=dates.count { value->
            val date=value.atZone(zone)
            Astronomy.calculate(date.toLocalDate(),lat,lon,zone).windows.any {
                val time=date.toLocalTime()
                if(it.end<it.start)time>=it.start || time<=it.end else time>=it.start && time<=it.end
            }
        }
        val band=dates.groupingBy { it.atZone(zone).hour/4 }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<Int,Int>>{it.value}.thenBy { it.key }).firstOrNull()?.key ?: 0
        return PersonalWindowSummary(matches,dates.size,band)
    }
}
