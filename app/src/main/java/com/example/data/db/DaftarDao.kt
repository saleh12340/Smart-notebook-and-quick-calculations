package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentEntryEntity
import com.example.data.model.DocumentType
import com.example.data.model.DocumentWithEntries
import kotlinx.coroutines.flow.Flow

@Dao
interface DaftarDao {

    @Transaction
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentWithEntries>>

    @Transaction
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    suspend fun getAllDocumentsDirect(): List<DocumentWithEntries>

    @Transaction
    @Query("SELECT * FROM documents WHERE docType = :type ORDER BY updatedAt DESC")
    fun getDocumentsByType(type: DocumentType): Flow<List<DocumentWithEntries>>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    fun getDocumentById(id: Long): Flow<DocumentWithEntries?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<DocumentEntryEntity>)

    @Query("DELETE FROM document_entries WHERE documentId = :documentId")
    suspend fun deleteEntriesForDocument(documentId: Long)

    @Transaction
    suspend fun saveDocumentWithEntries(
        document: DocumentEntity,
        entries: List<DocumentEntryEntity>
    ): Long {
        val docId = if (document.id == 0L) {
            insertDocument(document)
        } else {
            updateDocument(document)
            document.id
        }
        deleteEntriesForDocument(docId)
        val entriesWithId = entries.mapIndexed { index, entry ->
            entry.copy(id = 0, documentId = docId, sortOrder = index)
        }
        insertEntries(entriesWithId)
        return docId
    }

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getDocumentCount(): Int
}
