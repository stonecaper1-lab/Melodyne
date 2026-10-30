package org.melodyne.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Optional custom source: any compatible server the user runs themselves. The host is passed per call (@Url)
 * so the repository can fail over when one answers 402/429/5xx.
 */
interface HostedApi {
    @GET suspend fun searchSongs(
        @Url url: String, @Query("query") query: String,
        @Query("page") page: Int, @Query("limit") limit: Int,
    ): Envelope<SearchData>

    @GET suspend fun searchPlaylists(
        @Url url: String, @Query("query") query: String,
        @Query("page") page: Int, @Query("limit") limit: Int,
    ): Envelope<PlaylistSearchData>

    @GET suspend fun playlist(
        @Url url: String, @Query("id") id: String,
        @Query("page") page: Int, @Query("limit") limit: Int,
    ): Envelope<PlaylistDto>

    /** Path may differ per server; adjust in HostedRepository.lyrics() if needed. */
    @GET suspend fun lyrics(@Url url: String): Envelope<LyricsDto>
}
