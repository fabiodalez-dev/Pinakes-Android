package com.pinakes.app.data.network

import com.pinakes.app.data.model.*
import retrofit2.http.*

interface CollectionsApi {
    @GET("archives/health") suspend fun archivesHealth(): Envelope<CollectionHealth>
    @GET("desiderata/health") suspend fun desiderataHealth(): Envelope<CollectionHealth>
    @GET("archives") suspend fun archives(
        @Query("q") query: String? = null, @Query("level") level: String? = null,
        @Query("date_from") from: Int? = null, @Query("date_to") to: Int? = null,
        @Query("parent_id") parentId: Int? = null, @Query("cursor") cursor: String? = null,
    ): Envelope<List<ArchiveRecord>>
    @GET("archives/{id}") suspend fun archive(@Path("id") id: Int): Envelope<ArchiveRecord>
    @GET("desiderata") suspend fun wanted(@Query("q") query: String? = null, @Query("cursor") cursor: String? = null): Envelope<List<WantedBook>>
    @GET("desiderata/{id}") suspend fun wantedBook(@Path("id") id: Int): Envelope<WantedBook>
    @POST("desiderata/offers") suspend fun offer(@Body request: DonationOfferRequest): Envelope<DonationReceipt>
    @GET("desiderata/offers/{submission}") suspend fun offerStatus(@Path("submission") submission: String): Envelope<DonationReceipt>
}
