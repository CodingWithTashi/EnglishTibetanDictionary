package com.kharagedition.tibetandictionary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Usage on one local day (user.db), keyed `yyyy-MM-dd`; feeds the streak. */
@Entity(tableName = "activity_days")
data class ActivityDayEntity(
    @PrimaryKey @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "searches") val searches: Int,
    @ColumnInfo(name = "entries_opened") val entriesOpened: Int,
)
