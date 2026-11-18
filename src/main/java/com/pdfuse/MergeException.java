package com.pdfuse;

/** Erreur métier du merge, avec un message destiné à l'utilisateur. */
public final class MergeException extends Exception {

    public MergeException(String message) {
        super(message);
    }

    public MergeException(String message, Throwable cause) {
        super(message, cause);
    }
}
