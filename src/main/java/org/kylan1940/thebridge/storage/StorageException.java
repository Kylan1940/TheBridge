package org.kylan1940.thebridge.storage;

/** Thrown when the database fails. Wraps the SQL error so the rest of the plugin does not need java.sql. */
public class StorageException extends Exception {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
