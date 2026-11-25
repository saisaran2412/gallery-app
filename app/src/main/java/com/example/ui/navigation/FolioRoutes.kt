package com.example.ui.navigation

import android.net.Uri

object FolioRoutes {
    const val INTRO = "intro"
    const val GALLERY = "gallery"
    const val ALBUMS = "albums"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val VAULT = "vault"
    const val DUPLICATES = "duplicates"
    const val PEOPLE = "people"
    const val TRASH = "trash"
    const val AIR_SHARE = "air_share"

    const val VIEWER_PATTERN = "viewer/{mediaId}?source={source}&key={key}"
    const val ALBUM_DETAIL_PATTERN = "album_detail/{albumId}"
    const val FOLDER_DETAIL_PATTERN = "folder_detail/{bucketId}?name={name}"
    const val DAY_DETAIL_PATTERN = "day_detail/{dayKey}?title={title}"
    const val EDITOR_PATTERN = "editor/{mediaId}"
    const val COMPRESS_PATTERN = "compress/{mediaId}"
    const val SLIDESHOW_PATTERN = "slideshow?startId={startId}"

    fun viewer(mediaId: String, source: String = "gallery", key: String = ""): String =
        "viewer/${Uri.encode(mediaId)}?source=${Uri.encode(source)}&key=${Uri.encode(key)}"

    fun albumDetail(albumId: Long): String = "album_detail/$albumId"

    fun folderDetail(bucketId: String, name: String): String =
        "folder_detail/${Uri.encode(bucketId)}?name=${Uri.encode(name)}"

    fun dayDetail(dayKey: Int, title: String): String =
        "day_detail/$dayKey?title=${Uri.encode(title)}"

    fun editor(mediaId: String): String = "editor/${Uri.encode(mediaId)}"

    fun compress(mediaId: String): String = "compress/${Uri.encode(mediaId)}"

    fun slideshow(startId: String? = null): String =
        if (startId != null) "slideshow?startId=${Uri.encode(startId)}" else "slideshow"
}
