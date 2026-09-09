package uk.co.tacklebox.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import uk.co.tacklebox.app.data.CatchRow
import uk.co.tacklebox.app.services.Astronomy
import uk.co.tacklebox.app.ui.*
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
@Composable fun PersonalWindowsCard(catches:List<CatchRow>,lat:Double,lon:Double,zone:ZoneId) {
    HeritageCard {
        SectionLabel("Matched to your catches")
        if(catches.size<5)Text("Log ${5-catches.size} more catches to see a summary of your recorded catch times.")
        else {
            val summary=PersonalWindows.summary(catches.map { it.item.caughtAt },lat,lon,zone)
            Text("${summary.percent}%",style=MaterialTheme.typography.headlineLarge)
            Text("of your recorded catches fall within estimated major or minor windows")
            Text("Most recorded catches: %02d:00–%02d:00".format(summary.hourBand*4,(summary.hourBand*4+4)%24),color=Teal)
        }
        Text("Estimated using your current planning location, which may differ from past fishing spots. Catch counts do not measure fishing effort.",color=Muted)
    }
}
