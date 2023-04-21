package com.changdong.opc.controller;

import com.changdong.opc.ServerApplication;
import com.changdong.opc.bean.OpcGroupDto;
import com.changdong.opc.bean.OpcNodeDto;


import com.changdong.opc.server.ExampleNamespace;
import com.changdong.opc.server.ExampleServer;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/iot/opc/node")
public class NodeController {

    ExampleServer server = ServerApplication.server;
    ExampleNamespace exampleNamespace = server.getExampleNamespace();

    /**
     * 添加节点
     */
    @PostMapping()
    public void createNode(@RequestBody OpcNodeDto opcNodeDto){
        //ExampleNamespace exampleNamespace = server.getExampleNamespace();
        exampleNamespace.addNodeInGroup(opcNodeDto);
    }

    /**
     * 添加组
     */
    @PostMapping("/group")
    public void createGroup(@RequestBody OpcGroupDto opcGroupDto){
       //ExampleNamespace exampleNamespace = server.getExampleNamespace();
        System.out.println("调用了！！！！！！！！！！！！！！！");
        exampleNamespace.createGroup(opcGroupDto);
    }

    @DeleteMapping()
    public void deleteNode(@RequestParam Integer parentNamespace, @RequestParam String parentNodeId, @RequestParam Integer namespace, @RequestParam String nodeId){
        if (namespace == null || StringUtils.isEmpty(nodeId)){
            return;
        }
        exampleNamespace.deleteNode(parentNamespace, parentNodeId, namespace, nodeId);
    }
}
