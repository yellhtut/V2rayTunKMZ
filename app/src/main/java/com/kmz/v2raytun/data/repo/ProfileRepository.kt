package com.kmz.v2raytun.data.repo

import android.content.Context
import com.kmz.v2raytun.data.db.AppDatabase
import com.kmz.v2raytun.data.model.Profile
import kotlinx.coroutines.flow.Flow

/**
 * Single point of access to stored profiles. Wraps the DAO so callers never hold a Room
 * type directly, which keeps the ViewModels testable against a fake.
 */
class ProfileRepository(private val database: AppDatabase) {

    private val dao get() = database.profileDao()

    fun observeAll(): Flow<List<Profile>> = dao.observeAll()

    suspend fun getById(id: Long): Profile? = dao.getById(id)

    suspend fun add(profile: Profile): Long = dao.insert(profile)

    suspend fun addAll(profiles: List<Profile>): List<Long> = dao.insertAll(profiles)

    suspend fun update(profile: Profile) = dao.update(profile)

    suspend fun delete(profile: Profile) = dao.delete(profile)

    suspend fun count(): Int = dao.count()

    companion object {
        fun from(context: Context): ProfileRepository =
            ProfileRepository(AppDatabase.get(context))
    }
}
