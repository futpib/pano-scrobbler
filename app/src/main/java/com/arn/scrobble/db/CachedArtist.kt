package com.arn.scrobble.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arn.scrobble.api.lastfm.Artist
import com.arn.scrobble.api.lastfm.Track

@Entity(
    tableName = CachedArtistsDao.tableName,
    indices = [
        Index(value = ["artistName"], unique = true),
    ]
)
data class CachedArtist(
    @PrimaryKey(autoGenerate = true)
    var _id: Int = 0,

    var artistName: String = "",
    var artistMbid: String = "",
    var artistUrl: String = "",
    var userPlayCount: Int = -1,

    @ColumnInfo(defaultValue = "-1")
    var userPlayCountDirty: Int = -1,
) {
    companion object {
        fun CachedArtist.toArtist() = Artist(
            name = artistName,
            url = artistUrl,
            mbid = artistMbid,
            playcount = userPlayCount,
            userplaycount = userPlayCount,
        )

        fun Artist.toCachedArtist() = CachedArtist(
            artistName = name,
            artistUrl = url ?: "",
            artistMbid = mbid ?: "",
            userPlayCount = playcount ?: -1,
        )

        fun Track.toCachedArtist() = CachedArtist(
            artistName = artist.name,
            artistUrl = artist.url ?: "",
            artistMbid = artist.mbid ?: "",
            userPlayCount = playcount ?: -1,
        )
    }
}