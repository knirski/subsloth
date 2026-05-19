package net.subsloth.navigation

/**
 * Navigation route constants for the app's navigation graph.
 */
object Routes {
    const val LOGIN = "login"
    const val CATALOG = "catalog"
    const val CATALOG_HOME = "catalog/home"
    const val CATALOG_SEARCH = "catalog/search"
    const val MOVIE_DETAIL = "movie/{movieId}"
    const val SHOW_DETAIL = "show/{showId}"
    const val PLAYER = "player/{contentId}/{contentType}"
    const val LIBRARY = "library"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val AUTH_REPAIR = "auth_repair"
    const val OFFLINE_LIBRARY = "offline_library"

    fun movieDetail(movieId: String) = "movie/$movieId"
    fun showDetail(showId: String) = "show/$showId"
    fun player(contentId: String, contentType: String) = "player/$contentId/$contentType"
}
