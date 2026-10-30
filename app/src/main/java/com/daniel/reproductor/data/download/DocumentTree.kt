package com.daniel.reproductor.data.download

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Acceso a una carpeta elegida por el usuario con el selector del sistema (Storage Access Framework).
 * Solo trabaja con dos niveles: la raíz y una carpeta por playlist. Todas las operaciones bloquean,
 * se llaman desde [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO].
 */
class DocumentTree(private val resolver: ContentResolver, val uri: Uri) {

    private val rootId: String = DocumentsContract.getTreeDocumentId(uri)

    data class Entry(val documentId: String, val name: String, val isDirectory: Boolean)

    /** Hijos directos de [parentId] (la raíz si es null). */
    fun list(parentId: String? = null): List<Entry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, parentId ?: rootId)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        return resolver.query(children, projection, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Entry(c.getString(0), c.getString(1).orEmpty(), c.getString(2) == Document.MIME_TYPE_DIR))
                }
            }
        }.orEmpty()
    }

    /** Si la carpeta sigue existiendo y la app conserva el permiso. */
    fun isAccessible(): Boolean = runCatching { list(); true }.getOrDefault(false)

    fun findFolder(name: String): Entry? = list().firstOrNull { it.isDirectory && StorageNames.sameFolder(it.name, name) }

    fun ensureFolder(name: String): Entry = findFolder(name)
        ?: create(rootId, Document.MIME_TYPE_DIR, name).let { Entry(DocumentsContract.getDocumentId(it), name, true) }

    fun findFile(folderId: String, name: String): Entry? = list(folderId).firstOrNull { !it.isDirectory && it.name == name }

    /** URI del archivo `<folder>/<name>`, o null si ya no existe. */
    fun locate(folder: String, name: String): Uri? =
        findFolder(folder)?.let { findFile(it.documentId, name) }?.let { documentUri(it.documentId) }

    /** Copia [source] dentro de [folder]. Devuelve el nombre final (el sistema lo cambia si ya existe). */
    fun importFile(source: File, folder: String, name: String, mimeType: String): String =
        source.inputStream().use { write(it, folder, name, mimeType) }

    fun write(input: InputStream, folder: String, name: String, mimeType: String): String {
        val target = create(ensureFolder(folder).documentId, mimeType, name)
        try {
            resolver.openOutputStream(target, "w")?.use { input.copyTo(it) } ?: throw FileNotFoundException(name)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw e
        }
        return displayName(target) ?: name
    }

    fun open(folder: String, name: String): InputStream =
        locate(folder, name)?.let(resolver::openInputStream) ?: throw FileNotFoundException("$folder/$name")

    /**
     * Mueve `<from>/<name>` a la carpeta [to]. Intenta mover sin copiar y, si el proveedor no lo
     * permite o ya hay un archivo con ese nombre, copia y borra. Devuelve el nombre final.
     */
    fun move(from: String, name: String, to: String): String {
        val source = findFolder(from) ?: throw FileNotFoundException(from)
        val file = findFile(source.documentId, name) ?: throw FileNotFoundException("$from/$name")
        val target = ensureFolder(to)
        if (findFile(target.documentId, name) == null) {
            val moved = runCatching {
                DocumentsContract.moveDocument(
                    resolver, documentUri(file.documentId), documentUri(source.documentId), documentUri(target.documentId),
                )
            }.getOrNull()
            if (moved != null) return displayName(moved) ?: name
        }
        val mime = resolver.getType(documentUri(file.documentId)) ?: "application/octet-stream"
        val finalName = resolver.openInputStream(documentUri(file.documentId))!!.use { write(it, to, name, mime) }
        delete(from, name)
        return finalName
    }

    /** Renombra la carpeta de una playlist. Falla si no existe o si ya hay otra con el nombre nuevo. */
    fun renameFolder(from: String, to: String): Boolean {
        val folder = findFolder(from) ?: return false
        if (!StorageNames.sameFolder(from, to) && findFolder(to) != null) return false
        return runCatching { DocumentsContract.renameDocument(resolver, documentUri(folder.documentId), to) != null }
            .getOrDefault(false)
    }

    fun delete(folder: String, name: String) {
        locate(folder, name)?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
    }

    /** Borra la carpeta solo si está vacía: nunca se toca lo que el usuario haya puesto ahí. */
    fun deleteFolderIfEmpty(name: String) {
        val folder = findFolder(name) ?: return
        if (list(folder.documentId).isEmpty()) {
            runCatching { DocumentsContract.deleteDocument(resolver, documentUri(folder.documentId)) }
        }
    }

    /** Nombre legible de la carpeta elegida, para mostrarlo en Ajustes. */
    fun describe(): String = StorageNames.describeTree(rootId)

    private fun create(parentId: String, mimeType: String, name: String): Uri =
        DocumentsContract.createDocument(resolver, documentUri(parentId), mimeType, name)
            ?: throw FileNotFoundException("No se pudo crear $name")

    private fun documentUri(documentId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)

    private fun displayName(document: Uri): String? =
        resolver.query(document, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
