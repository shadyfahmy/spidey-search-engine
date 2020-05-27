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
    int page;

    public History(){}

    public History(int user, int page) {
        this.user = user;
        this.page = page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public void setUser(int user) {
        this.user = user;
    }


    public int getUser() {
        return user;
    }

    public int getPage() {
        return page;
    }
}
