package com.zoho.mail;

import java.io.Serializable;

public class SerializableMail implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String subject;
    private String content;
    
    public SerializableMail(String subject, String content) {
        this.subject = subject;
        this.content = content;
    }

    public String getSubject() {
        return subject;
    }

    public String getContent() {
        return content;
    }
}

