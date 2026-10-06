package com.rkant.bhajanapp.model;

import java.util.List;

public class Bhajan {
    public String id;
    public String titleNepali;
    public String titleEnglish;
    public String category;
    public String type;


    public Bhajan(String id, String titleNepali, String titleEnglish, String category, String type) {
        this.id = id;
        this.titleNepali = titleNepali;
        this.titleEnglish = titleEnglish;
        this.category = category;
        this.type = type;
    }
}