package app.arthik.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Dates as epoch days, date-times as epoch seconds of the local wall-clock time (sortable, comparable). */
class Converters {
    @TypeConverter fun dateToLong(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)
    @TypeConverter fun dateTimeToLong(d: LocalDateTime?): Long? = d?.toEpochSecond(ZoneOffset.UTC)
    @TypeConverter fun longToDateTime(v: Long?): LocalDateTime? = v?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }
}
