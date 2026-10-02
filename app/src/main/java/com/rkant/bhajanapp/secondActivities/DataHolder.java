package com.rkant.bhajanapp.secondActivities;

public class DataHolder {

    String bhajan_name_nepali;
    String bhajan_name_english;
    String id;
    String bhajan_type;
    String bhajan;

    public DataHolder(String bhajan_name_nepali, String bhajan_name_english, String id, String bhajan_type) {
        this(bhajan_name_nepali, bhajan_name_english, id, "others",bhajan_type);
    }

    public DataHolder(String bhajan_name_nepali, String bhajan_name_english, String id, String bhajan, String bhajan_type) {
        this.bhajan_name_nepali = bhajan_name_nepali;
        this.bhajan_name_english = bhajan_name_english;
        this.id = id;
        this.bhajan_type=bhajan_type;
        this.bhajan = bhajan;
    }

    public String getId() { return id; }

    public String getBhajan_name_english() {
        return bhajan_name_english;
    }

    public String getBhajan_name_nepali() {
        return bhajan_name_nepali;
    }

    public String getBhajan() {
        return bhajan;
    }
    public String getBhajan_type() {

        return bhajan_type;
    }



}  