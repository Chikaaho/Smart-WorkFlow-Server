package com.sw.ck.common.page;

import lombok.Data;

@Data
public class PageParam {

    private long pageNum = 1;
    private long pageSize = 10;
}
