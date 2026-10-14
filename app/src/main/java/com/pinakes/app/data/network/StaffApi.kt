package com.pinakes.app.data.network

import com.pinakes.app.data.model.AddCopiesRequest
import com.pinakes.app.data.model.AddCopiesResult
import com.pinakes.app.data.model.CoverResult
import com.pinakes.app.data.model.CoverUrlRequest
import com.pinakes.app.data.model.DeleteResult
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.IsbnLookup
import com.pinakes.app.data.model.NextPosition
import com.pinakes.app.data.model.Shelf
import com.pinakes.app.data.model.ShelfLevel
import com.pinakes.app.data.model.ShelfLevelWrite
import com.pinakes.app.data.model.ShelfWrite
import com.pinakes.app.data.model.StaffAuthor
import com.pinakes.app.data.model.StaffAuthorRow
import com.pinakes.app.data.model.StaffAuthorWrite
import com.pinakes.app.data.model.StaffBook
import com.pinakes.app.data.model.StaffBookRow
import com.pinakes.app.data.model.StaffGenreCreated
import com.pinakes.app.data.model.StaffGenreNode
import com.pinakes.app.data.model.StaffGenreWrite
import com.pinakes.app.data.model.StaffLookups
import com.pinakes.app.data.model.StaffPublisher
import com.pinakes.app.data.model.StaffPublisherWrite
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Staff content management (Mobile API 1.6.0). Every route requires a bearer token of an
 * `admin` or `staff` user; anyone else gets 403 `forbidden`. Same base URL and token as
 * [PinakesApi].
 */
interface StaffApi {

    @GET("staff/lookups")
    suspend fun lookups(): Envelope<StaffLookups>

    @GET("staff/isbn/{isbn}")
    suspend fun isbnLookup(@Path("isbn") isbn: String): Envelope<IsbnLookup>

    @GET("staff/books")
    suspend fun books(
        @Query("q") q: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("sort") sort: String? = null,
        @Query("author_id") authorId: Int? = null,
        @Query("publisher") publisher: String? = null,
    ): Envelope<List<StaffBookRow>>

    @GET("staff/books/{id}")
    suspend fun book(@Path("id") id: Int): Envelope<StaffBook>

    /** Body is built by [com.pinakes.app.data.staff.StaffBookWrite] (only the sent keys change). */
    @POST("staff/books")
    suspend fun createBook(@Body body: JsonObject): Envelope<StaffBook>

    @PUT("staff/books/{id}")
    suspend fun updateBook(@Path("id") id: Int, @Body body: JsonObject): Envelope<StaffBook>

    @DELETE("staff/books/{id}")
    suspend fun deleteBook(@Path("id") id: Int): Envelope<DeleteResult>

    @POST("staff/books/{id}/copies")
    suspend fun addCopies(@Path("id") id: Int, @Body body: AddCopiesRequest): Envelope<AddCopiesResult>

    @Multipart
    @POST("staff/books/{id}/cover")
    suspend fun uploadCover(@Path("id") id: Int, @Part cover: MultipartBody.Part): Envelope<CoverResult>

    @POST("staff/books/{id}/cover")
    suspend fun coverFromUrl(@Path("id") id: Int, @Body body: CoverUrlRequest): Envelope<CoverResult>

    @GET("staff/authors")
    suspend fun authors(@Query("q") q: String? = null, @Query("limit") limit: Int? = null): Envelope<List<StaffAuthorRow>>

    @GET("staff/authors/{id}")
    suspend fun author(@Path("id") id: Int): Envelope<StaffAuthor>

    @POST("staff/authors")
    suspend fun createAuthor(@Body body: StaffAuthorWrite): Envelope<StaffAuthor>

    @PUT("staff/authors/{id}")
    suspend fun updateAuthor(@Path("id") id: Int, @Body body: StaffAuthorWrite): Envelope<StaffAuthor>

    @GET("staff/publishers")
    suspend fun publishers(@Query("q") q: String? = null, @Query("limit") limit: Int? = null): Envelope<List<StaffPublisher>>

    @GET("staff/publishers/{id}")
    suspend fun publisher(@Path("id") id: Int): Envelope<StaffPublisher>

    @POST("staff/publishers")
    suspend fun createPublisher(@Body body: StaffPublisherWrite): Envelope<StaffPublisher>

    @PUT("staff/publishers/{id}")
    suspend fun updatePublisher(@Path("id") id: Int, @Body body: StaffPublisherWrite): Envelope<StaffPublisher>

    @GET("staff/genres")
    suspend fun genres(): Envelope<List<StaffGenreNode>>

    @POST("staff/genres")
    suspend fun createGenre(@Body body: StaffGenreWrite): Envelope<StaffGenreCreated>

    @GET("staff/shelves")
    suspend fun shelves(): Envelope<List<Shelf>>

    @POST("staff/shelves")
    suspend fun createShelf(@Body body: ShelfWrite): Envelope<Shelf>

    @POST("staff/shelves/{id}/levels")
    suspend fun createLevel(@Path("id") shelfId: Int, @Body body: ShelfLevelWrite): Envelope<ShelfLevel>

    @GET("staff/shelves/next-position")
    suspend fun nextPosition(
        @Query("shelf_id") shelfId: Int,
        @Query("level_id") levelId: Int,
        @Query("book_id") bookId: Int? = null,
    ): Envelope<NextPosition>
}
