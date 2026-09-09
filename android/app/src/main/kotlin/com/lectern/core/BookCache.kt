package com.lectern.core

/**
 * Keeps the last book that was tokenized.
 *
 * Opening a book means reading its text off disk and running [tokenize] over
 * every word of it — a noticeable wait for a novel. Stepping back to the shelf
 * and into the same book again is the commonest thing a reader does, so that
 * work is held onto rather than repeated. One book at a time: the tokens of a
 * long book are not small, and a second one is never the one being returned to.
 */
class BookCache {

    private var heldId: String? = null
    private var held: Book? = null

    fun get(bookId: String): Book? = held.takeIf { heldId == bookId }

    fun put(bookId: String, book: Book) {
        heldId = bookId
        held = book
    }

    /** Drops [bookId] if it is the one held — its text has changed or gone. */
    fun forget(bookId: String) {
        if (heldId == bookId) clear()
    }

    fun clear() {
        heldId = null
        held = null
    }
}
