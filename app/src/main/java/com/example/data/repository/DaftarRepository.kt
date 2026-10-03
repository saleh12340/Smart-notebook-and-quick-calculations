package com.example.data.repository

import com.example.data.db.DaftarDao
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentEntryEntity
import com.example.data.model.DocumentType
import com.example.data.model.DocumentWithEntries
import kotlinx.coroutines.flow.Flow

class DaftarRepository(val dao: DaftarDao) {

    val allDocuments: Flow<List<DocumentWithEntries>> = dao.getAllDocuments()

    fun getDocumentsByType(type: DocumentType): Flow<List<DocumentWithEntries>> =
        dao.getDocumentsByType(type)

    fun getDocumentById(id: Long): Flow<DocumentWithEntries?> =
        dao.getDocumentById(id)

    suspend fun saveDocument(
        document: DocumentEntity,
        entries: List<DocumentEntryEntity>
    ): Long {
        return dao.saveDocumentWithEntries(document, entries)
    }

    suspend fun deleteDocument(id: Long) {
        dao.deleteDocumentById(id)
    }
}
