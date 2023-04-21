package com.changdong.opc.bean;

import lombok.Data;

@Data
public class OpcGroupDto {

    private Integer parentNameSpace = 2;

    private String parentNodeId;

    private String identifier;

    private String name;

}
