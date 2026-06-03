package edu.sustech.cs307.record;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RID {
    public int pageNum;
    public int slotNum;

    @JsonCreator
    public RID(@JsonProperty("pageNum") int page_no,
               @JsonProperty("slotNum") int slot_no) {
        this.pageNum = page_no;
        this.slotNum = slot_no;
    }

    public RID(RID rid) {
        this.pageNum = rid.pageNum;
        this.slotNum = rid.slotNum;
    }
}
