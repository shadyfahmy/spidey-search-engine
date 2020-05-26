package com.search.queryprocessor.entity;


import lombok.ToString;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

//@Entity
//@Table(name = "history", schema = "search")
//@ToString
public class History {
    int user;
    int url;

    public History(){}

    public History(int user, int url) {
        this.user = user;
        this.url = url;
    }

    public void setUrl(int url) {
        this.url = url;
    }

    public void setUser(int user) {
        this.user = user;
    }

    public int getUrl() {
        return url;
    }

    public int getUser() {
        return user;
    }
}
