package com.nextz.kanomtts.data.repository

/** Deletes a book and all of its dependents (no Room FK cascade exists). */
interface BookDeleter {
    suspend fun delete(bookId: String)
}
