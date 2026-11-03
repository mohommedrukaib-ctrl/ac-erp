package com.aceerp.model;

public class Warehouse {
    private int id;
    private String code;
    private String name;
    private String locationType = "STORE";
    private String address;
    private boolean active = true;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocationType() { return locationType; }
    public void setLocationType(String locationType) { this.locationType = locationType; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override public String toString() { return "[" + code + "] " + name; }
}


