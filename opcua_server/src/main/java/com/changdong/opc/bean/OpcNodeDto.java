package com.changdong.opc.bean;

import lombok.Data;

@Data
public class OpcNodeDto {

    private Integer parentNameSpace =2;

    private String parentNodeId;

    private String identifier;

    private String name;

    private Integer accessLevel = 0;

    private String dataType;
}
